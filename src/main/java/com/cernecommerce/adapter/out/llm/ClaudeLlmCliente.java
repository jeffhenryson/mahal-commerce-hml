package com.cernecommerce.adapter.out.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Claude (Anthropic) pelo SDK oficial ({@code anthropic-java}). Conduz o laço de ferramentas de
 * um turno: stream da resposta, execução das ferramentas pedidas (pelo ouvinte, como o usuário
 * logado) e nova chamada com os resultados, até o modelo terminar.
 *
 * <p>O cliente HTTP é criado por chave (a loja pode trocar a chave a qualquer momento) e
 * reaproveitado; a chave em si nunca vai para log.</p>
 */
@Component
public class ClaudeLlmCliente implements ClienteLlm, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmCliente.class);

    /** Recusa do modelo cai para outro modelo no servidor, roteado pela categoria (beta). */
    static final String BETA_FALLBACK = "server-side-fallback-2026-07-01";

    private final LlmSupport.Clientes<AnthropicClient> clientes;
    private final String effort;
    private final boolean fallbackRecusa;

    public ClaudeLlmCliente(
            @Value("${assistente.llm.timeout:PT3M}") Duration timeout,
            @Value("${assistente.llm.max-retries:2}") int maxRetries,
            @Value("${assistente.anthropic.effort:${assistente.effort:medium}}") String effort,
            @Value("${assistente.fallback-recusa:true}") boolean fallbackRecusa) {
        this.clientes = new LlmSupport.Clientes<>(apiKey -> AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build(), AnthropicClient::close);
        this.effort = effort;
        this.fallbackRecusa = fallbackRecusa;
    }

    @Override
    public ProvedorIa provedor() {
        return ProvedorIa.ANTHROPIC;
    }

    @Override
    public RespostaTurno conversar(AssistantCredentials credenciais, Requisicao req, Ouvinte ouvinte) {
        AnthropicClient client = clientes.de(credenciais.apiKey());
        List<MessageParam> conversa = new ArrayList<>();
        for (MensagemHistorico m : req.historico()) {
            conversa.add(MessageParam.builder()
                    .role(m.doUsuario() ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                    .content(m.texto())
                    .build());
        }
        conversa.add(MessageParam.builder().role(MessageParam.Role.USER).content(req.mensagemUsuario()).build());

        try {
            for (int i = 0; i < req.maxIteracoes(); i++) {
                Message resposta = stream(client, base(credenciais, req).messages(conversa).build(), ouvinte);
                if (resposta == null) {
                    return new RespostaTurno("CANCELADO");
                }
                String parada = resposta.stopReason().map(StopReason::asString).orElse("end_turn");
                if ("refusal".equals(parada)) {
                    return new RespostaTurno("RECUSA");
                }
                if ("max_tokens".equals(parada)) {
                    return new RespostaTurno("LIMITE_TOKENS");
                }
                if (!"tool_use".equals(parada)) {
                    return new RespostaTurno("CONCLUIDO");
                }
                // Append-only: a resposta inteira (com os blocos de raciocínio) volta como está.
                conversa.add(resposta.toParam());
                List<ContentBlockParam> resultados = new ArrayList<>();
                for (ContentBlock bloco : resposta.content()) {
                    if (bloco.toolUse().isEmpty()) {
                        continue;
                    }
                    ToolUseBlock uso = bloco.toolUse().get();
                    ResultadoFerramenta r = ouvinte.cancelado()
                            ? ResultadoFerramenta.falha("Cancelado pelo usuário.")
                            : ouvinte.ferramenta(uso.name(), inputJson(uso));
                    resultados.add(ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                            .toolUseId(uso.id())
                            .content(r.conteudo())
                            .isError(r.erro())
                            .build()));
                }
                if (ouvinte.cancelado()) {
                    return new RespostaTurno("CANCELADO");
                }
                // Todos os resultados numa única mensagem do usuário.
                conversa.add(MessageParam.builder().role(MessageParam.Role.USER).contentOfBlockParams(resultados).build());
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
            clientes.de(credenciais.apiKey()).messages().create(MessageCreateParams.builder()
                    .model(credenciais.model())
                    .maxTokens(256L)
                    .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of("low")).build())
                    .addUserMessage("Responda só com a palavra ok.")
                    .build());
            return IntegrationTestResult.ok(credenciais.model());
        } catch (RuntimeException ex) {
            return IntegrationTestResult.failed(traduzir(ex).getMessage(), credenciais.model());
        }
    }

    private MessageCreateParams.Builder base(AssistantCredentials credenciais, Requisicao req) {
        List<Tool> tools = req.ferramentas().stream().map(ClaudeLlmCliente::tool).toList();
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(credenciais.model())
                .maxTokens(req.maxTokens())
                // System e ferramentas são fixos: o breakpoint no system cacheia os dois (ordem tools → system).
                .systemOfTextBlockParams(List.of(TextBlockParam.builder()
                        .text(req.systemPrompt())
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build()))
                // Cache automático do histórico, que cresce a cada volta do laço.
                .cacheControl(CacheControlEphemeral.builder().build())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.of(effort)).build());
        tools.forEach(b::addTool);
        if (fallbackRecusa) {
            b.putAdditionalHeader("anthropic-beta", BETA_FALLBACK);
            b.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }
        return b;
    }

    /** Stream de uma chamada. Devolve a mensagem completa, ou {@code null} se o cliente desconectou. */
    private static Message stream(AnthropicClient client, MessageCreateParams params, Ouvinte ouvinte) {
        MessageAccumulator acumulador = MessageAccumulator.create();
        try (StreamResponse<RawMessageStreamEvent> resposta = client.messages().createStreaming(params)) {
            Iterator<RawMessageStreamEvent> eventos = resposta.stream().iterator();
            while (eventos.hasNext()) {
                RawMessageStreamEvent evento = eventos.next();
                acumulador.accumulate(evento);
                evento.contentBlockDelta()
                        .flatMap(d -> d.delta().text())
                        .ifPresent(t -> ouvinte.texto(t.text()));
                if (ouvinte.cancelado()) {
                    return null;
                }
            }
        }
        return acumulador.message();
    }

    private static Tool tool(FerramentaDef def) {
        Map<String, Object> schema = LlmSupport.schema(def);
        Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
        if (schema.get("properties") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> props.putAdditionalProperty(String.valueOf(k), JsonValue.from(v)));
        }
        Tool.InputSchema.Builder input = Tool.InputSchema.builder()
                .properties(props.build())
                .putAdditionalProperty("additionalProperties", JsonValue.from(false));
        if (schema.get("required") instanceof List<?> required) {
            input.required(required.stream().map(String::valueOf).toList());
        }
        return Tool.builder()
                .name(def.nome())
                .description(def.descricao())
                .inputSchema(input.build())
                // Sem buffer do servidor: o JSON chega sem validação e a ferramenta valida ao ler.
                .eagerInputStreaming(true)
                .build();
    }

    private static String inputJson(ToolUseBlock uso) {
        try {
            return LlmSupport.MAPPER.writeValueAsString(uso._input().convert(Object.class));
        } catch (Exception ex) {
            // JSON truncado ou inválido: a ferramenta recusa e o modelo tenta de novo.
            return "";
        }
    }

    private static AssistenteProvedorException traduzir(RuntimeException ex) {
        if (ex instanceof UnauthorizedException || ex instanceof PermissionDeniedException) {
            return LlmSupport.chaveRecusada(ProvedorIa.ANTHROPIC, ex);
        }
        if (ex instanceof AnthropicServiceException service) {
            log.warn("assistente.llm.erro provedor=ANTHROPIC status={} tipo={}", service.statusCode(),
                    service.errorType().orElse(null));
            return LlmSupport.porStatus(ProvedorIa.ANTHROPIC, service.statusCode(), ex);
        }
        if (ex instanceof AnthropicIoException) {
            return LlmSupport.semConexao(ProvedorIa.ANTHROPIC, ex);
        }
        return LlmSupport.desconhecido(ProvedorIa.ANTHROPIC, ex);
    }

    @Override
    public void destroy() {
        clientes.fecharTodos();
    }
}
