package com.cernecommerce.adapter.out.llm;

import com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.FerramentaDef;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.MensagemHistorico;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Ouvinte;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Requisicao;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.RespostaTurno;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.ResultadoFerramenta;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import com.google.genai.types.ThinkingConfig;
import com.google.genai.types.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gemini (Google) pelo SDK oficial ({@code google-genai}), via {@code generateContentStream}. A
 * chamada automática de funções do SDK fica <b>desligada</b>: quem executa as ferramentas é o
 * ouvinte, como o usuário logado. As partes da resposta do modelo voltam ao histórico como vieram
 * (inclusive as assinaturas de raciocínio, que os modelos novos exigem de volta).
 */
@Component
public class GeminiLlmCliente implements ClienteLlm, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmCliente.class);

    private static final Set<FinishReason.Known> RECUSAS = Set.of(FinishReason.Known.SAFETY,
            FinishReason.Known.PROHIBITED_CONTENT, FinishReason.Known.BLOCKLIST, FinishReason.Known.SPII,
            FinishReason.Known.RECITATION);

    private final LlmSupport.Clientes<Client> clientes;
    /** {@code low}/{@code high} nos modelos que aceitam; em branco não é enviado. */
    private final String thinkingLevel;

    public GeminiLlmCliente(
            @Value("${assistente.llm.timeout:PT3M}") Duration timeout,
            @Value("${assistente.gemini.thinking-level:}") String thinkingLevel) {
        this.clientes = new LlmSupport.Clientes<>(apiKey -> Client.builder()
                .apiKey(apiKey)
                .httpOptions(HttpOptions.builder().timeout((int) timeout.toMillis()).build())
                .build(), Client::close);
        this.thinkingLevel = thinkingLevel == null ? "" : thinkingLevel.strip();
    }

    @Override
    public ProvedorIa provedor() {
        return ProvedorIa.GEMINI;
    }

    @Override
    public RespostaTurno conversar(AssistantCredentials credenciais, Requisicao req, Ouvinte ouvinte) {
        Client client = clientes.de(credenciais.apiKey());
        List<Content> conversa = new ArrayList<>();
        for (MensagemHistorico m : req.historico()) {
            conversa.add(Content.builder().role(m.doUsuario() ? "user" : "model")
                    .parts(Part.fromText(m.texto())).build());
        }
        conversa.add(Content.builder().role("user").parts(Part.fromText(req.mensagemUsuario())).build());
        GenerateContentConfig config = config(req);

        try {
            for (int i = 0; i < req.maxIteracoes(); i++) {
                Resposta resposta = stream(client, credenciais.model(), conversa, config, ouvinte);
                if (resposta == null) {
                    return new RespostaTurno("CANCELADO");
                }
                if (resposta.parada() != null && RECUSAS.contains(resposta.parada())) {
                    return new RespostaTurno("RECUSA");
                }
                if (resposta.parada() == FinishReason.Known.MAX_TOKENS) {
                    return new RespostaTurno("LIMITE_TOKENS");
                }
                List<FunctionCall> chamadas = resposta.partes().stream()
                        .flatMap(p -> p.functionCall().stream())
                        .toList();
                if (chamadas.isEmpty()) {
                    return new RespostaTurno("CONCLUIDO");
                }
                conversa.add(Content.builder().role("model").parts(resposta.partes()).build());
                List<Part> respostas = new ArrayList<>();
                for (FunctionCall chamada : chamadas) {
                    String nome = chamada.name().orElse("");
                    ResultadoFerramenta r = ouvinte.cancelado()
                            ? ResultadoFerramenta.falha("Cancelado pelo usuário.")
                            : ouvinte.ferramenta(nome, argumentos(chamada));
                    FunctionResponse.Builder fr = FunctionResponse.builder()
                            .name(nome)
                            .response(Map.of(r.erro() ? "error" : "output", r.conteudo()));
                    chamada.id().ifPresent(fr::id);
                    respostas.add(Part.builder().functionResponse(fr.build()).build());
                }
                if (ouvinte.cancelado()) {
                    return new RespostaTurno("CANCELADO");
                }
                // Todas as respostas de função num único conteúdo, na ordem das chamadas.
                conversa.add(Content.builder().role("user").parts(respostas).build());
            }
            return new RespostaTurno("LIMITE_ITERACOES");
        } catch (AssistenteProvedorException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw traduzir(ex);
        }
    }

    @Override
    public IntegrationTestResult testar(AssistantCredentials credenciais) {
        try {
            clientes.de(credenciais.apiKey()).models.generateContent(credenciais.model(),
                    "Responda só com a palavra ok.",
                    GenerateContentConfig.builder().maxOutputTokens(256).build());
            return IntegrationTestResult.ok(credenciais.model());
        } catch (RuntimeException ex) {
            return IntegrationTestResult.failed(traduzir(ex).getMessage(), credenciais.model());
        }
    }

    private GenerateContentConfig config(Requisicao req) {
        List<FunctionDeclaration> declaracoes = req.ferramentas().stream().map(GeminiLlmCliente::declaracao).toList();
        GenerateContentConfig.Builder b = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(req.systemPrompt())))
                .tools(List.of(Tool.builder().functionDeclarations(declaracoes).build()))
                .automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build())
                .maxOutputTokens((int) Math.min(req.maxTokens(), Integer.MAX_VALUE));
        if (!thinkingLevel.isEmpty()) {
            b.thinkingConfig(ThinkingConfig.builder().thinkingLevel(thinkingLevel).build());
        }
        return b.build();
    }

    /** Partes da resposta do modelo (na ordem) e o motivo de parada. */
    private record Resposta(List<Part> partes, FinishReason.Known parada) { }

    /**
     * Stream de uma chamada. Devolve as partes acumuladas, ou {@code null} se o turno foi cancelado.
     * O uso (tokens) vem acumulado nos pedaços; o último vale para a chamada inteira.
     */
    private static Resposta stream(Client client, String modelo, List<Content> conversa, GenerateContentConfig config,
            Ouvinte ouvinte) {
        List<Part> partes = new ArrayList<>();
        FinishReason.Known parada = null;
        GenerateContentResponseUsageMetadata uso = null;
        try (ResponseStream<GenerateContentResponse> resposta =
                     client.models.generateContentStream(modelo, conversa, config)) {
            for (GenerateContentResponse pedaco : resposta) {
                if (pedaco.usageMetadata().isPresent()) {
                    uso = pedaco.usageMetadata().get();
                }
                Candidate candidato = pedaco.candidates().filter(c -> !c.isEmpty()).map(c -> c.get(0)).orElse(null);
                if (candidato == null) {
                    continue;
                }
                List<Part> novas = candidato.content().flatMap(Content::parts).orElse(List.of());
                for (Part parte : novas) {
                    partes.add(parte);
                    boolean pensamento = parte.thought().orElse(false);
                    if (!pensamento) {
                        parte.text().filter(t -> !t.isEmpty()).ifPresent(ouvinte::texto);
                    }
                }
                if (candidato.finishReason().isPresent()) {
                    parada = candidato.finishReason().get().knownEnum();
                }
                if (ouvinte.cancelado()) {
                    informarUso(uso, ouvinte);
                    return null;
                }
            }
        }
        informarUso(uso, ouvinte);
        return new Resposta(partes, parada);
    }

    private static void informarUso(GenerateContentResponseUsageMetadata uso, Ouvinte ouvinte) {
        if (uso == null) {
            return;
        }
        long cache = uso.cachedContentTokenCount().orElse(0);
        long entrada = uso.promptTokenCount().orElse(0) + uso.toolUsePromptTokenCount().orElse(0);
        long saida = uso.candidatesTokenCount().orElse(0) + uso.thoughtsTokenCount().orElse(0);
        ouvinte.uso(Math.max(0, entrada - cache), saida, cache);
    }

    private static FunctionDeclaration declaracao(FerramentaDef def) {
        Map<String, Object> schema = new LinkedHashMap<>(LlmSupport.schema(def));
        // O Gemini recusa additionalProperties no schema das funções.
        schema.remove("additionalProperties");
        return FunctionDeclaration.builder()
                .name(def.nome())
                .description(def.descricao())
                .parametersJsonSchema(schema)
                .build();
    }

    private static String argumentos(FunctionCall chamada) {
        try {
            return LlmSupport.MAPPER.writeValueAsString(chamada.args().orElse(Map.of()));
        } catch (Exception ex) {
            return "";
        }
    }

    private static AssistenteProvedorException traduzir(RuntimeException ex) {
        if (ex instanceof ApiException api) {
            log.warn("assistente.llm.erro provedor=GEMINI status={} estado={}", api.code(), api.status());
            // A API do Gemini responde chave inválida como 400 INVALID_ARGUMENT "API key not valid".
            boolean chave = api.code() == 401 || api.code() == 403
                    || (api.message() != null && api.message().toLowerCase().contains("api key"));
            return chave ? LlmSupport.chaveRecusada(ProvedorIa.GEMINI, ex)
                    : LlmSupport.porStatus(ProvedorIa.GEMINI, api.code(), ex);
        }
        if (ex instanceof GenAiIOException) {
            return LlmSupport.semConexao(ProvedorIa.GEMINI, ex);
        }
        return LlmSupport.desconhecido(ProvedorIa.GEMINI, ex);
    }

    @Override
    public void destroy() {
        clientes.fecharTodos();
    }
}
