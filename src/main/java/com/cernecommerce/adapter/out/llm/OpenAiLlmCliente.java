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
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.core.http.StreamResponse;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.UnauthorizedException;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
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
 * ChatGPT (OpenAI) pelo SDK oficial ({@code openai-java}), via Chat Completions com stream. Mesmo
 * laço do Claude: o modelo pede ferramentas ({@code finish_reason=tool_calls}), o ouvinte executa
 * como o usuário logado e cada resultado volta como mensagem {@code tool} com o {@code tool_call_id}.
 */
@Component
public class OpenAiLlmCliente implements ClienteLlm, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmCliente.class);

    private final LlmSupport.Clientes<OpenAIClient> clientes;
    /** Só para modelos de raciocínio; em branco não é enviado. */
    private final String reasoningEffort;

    public OpenAiLlmCliente(
            @Value("${assistente.llm.timeout:PT3M}") Duration timeout,
            @Value("${assistente.llm.max-retries:2}") int maxRetries,
            @Value("${assistente.openai.reasoning-effort:}") String reasoningEffort) {
        this.clientes = new LlmSupport.Clientes<>(apiKey -> OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(timeout)
                .maxRetries(maxRetries)
                .build(), OpenAIClient::close);
        this.reasoningEffort = reasoningEffort == null ? "" : reasoningEffort.strip();
    }

    @Override
    public ProvedorIa provedor() {
        return ProvedorIa.OPENAI;
    }

    @Override
    public RespostaTurno conversar(AssistantCredentials credenciais, Requisicao req, Ouvinte ouvinte) {
        OpenAIClient client = clientes.de(credenciais.apiKey());
        List<ChatCompletionFunctionTool> tools = req.ferramentas().stream().map(OpenAiLlmCliente::tool).toList();
        List<ChatCompletionMessageParam> conversa = new ArrayList<>();
        conversa.add(ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(req.systemPrompt()).build()));
        for (MensagemHistorico m : req.historico()) {
            conversa.add(m.doUsuario() ? usuario(m.texto())
                    : ChatCompletionMessageParam.ofAssistant(
                            ChatCompletionAssistantMessageParam.builder().content(m.texto()).build()));
        }
        conversa.add(usuario(req.mensagemUsuario()));

        try {
            for (int i = 0; i < req.maxIteracoes(); i++) {
                ChatCompletionCreateParams.Builder params = ChatCompletionCreateParams.builder()
                        .model(credenciais.model())
                        .maxCompletionTokens(req.maxTokens())
                        .messages(conversa);
                tools.forEach(params::addTool);
                if (!reasoningEffort.isEmpty()) {
                    params.reasoningEffort(ReasoningEffort.of(reasoningEffort));
                }
                ChatCompletion resposta = stream(client, params.build(), ouvinte);
                if (resposta == null) {
                    return new RespostaTurno("CANCELADO");
                }
                if (resposta.choices().isEmpty()) {
                    return new RespostaTurno("CONCLUIDO");
                }
                ChatCompletion.Choice escolha = resposta.choices().get(0);
                String parada = escolha.finishReason().asString();
                if ("content_filter".equals(parada)) {
                    return new RespostaTurno("RECUSA");
                }
                if ("length".equals(parada)) {
                    return new RespostaTurno("LIMITE_TOKENS");
                }
                ChatCompletionMessage mensagem = escolha.message();
                List<ChatCompletionMessageToolCall> chamadas = mensagem.toolCalls().orElse(List.of());
                if (chamadas.isEmpty()) {
                    return new RespostaTurno("CONCLUIDO");
                }
                // A mensagem do assistente com os tool_calls volta inteira antes dos resultados.
                conversa.add(ChatCompletionMessageParam.ofAssistant(mensagem.toParam()));
                for (ChatCompletionMessageToolCall chamada : chamadas) {
                    if (!chamada.isFunction()) {
                        continue;
                    }
                    ChatCompletionMessageFunctionToolCall funcao = chamada.asFunction();
                    ResultadoFerramenta r = ouvinte.cancelado()
                            ? ResultadoFerramenta.falha("Cancelado pelo usuário.")
                            : ouvinte.ferramenta(funcao.function().name(), funcao.function().arguments());
                    conversa.add(ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                            .toolCallId(funcao.id())
                            .content(r.erro() ? "ERRO: " + r.conteudo() : r.conteudo())
                            .build()));
                }
                if (ouvinte.cancelado()) {
                    return new RespostaTurno("CANCELADO");
                }
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
            clientes.de(credenciais.apiKey()).chat().completions().create(ChatCompletionCreateParams.builder()
                    .model(credenciais.model())
                    .maxCompletionTokens(256L)
                    .addUserMessage("Responda só com a palavra ok.")
                    .build());
            return IntegrationTestResult.ok(credenciais.model());
        } catch (RuntimeException ex) {
            return IntegrationTestResult.failed(traduzir(ex).getMessage(), credenciais.model());
        }
    }

    /** Stream de uma chamada. Devolve a resposta completa, ou {@code null} se o cliente desconectou. */
    private static ChatCompletion stream(OpenAIClient client, ChatCompletionCreateParams params, Ouvinte ouvinte) {
        ChatCompletionAccumulator acumulador = ChatCompletionAccumulator.create();
        try (StreamResponse<ChatCompletionChunk> resposta = client.chat().completions().createStreaming(params)) {
            Iterator<ChatCompletionChunk> pedacos = resposta.stream().iterator();
            while (pedacos.hasNext()) {
                ChatCompletionChunk pedaco = pedacos.next();
                acumulador.accumulate(pedaco);
                for (ChatCompletionChunk.Choice escolha : pedaco.choices()) {
                    escolha.delta().content().filter(t -> !t.isEmpty()).ifPresent(ouvinte::texto);
                }
                if (ouvinte.cancelado()) {
                    return null;
                }
            }
        }
        return acumulador.chatCompletion();
    }

    private static ChatCompletionMessageParam usuario(String texto) {
        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder().content(texto).build());
    }

    private static ChatCompletionFunctionTool tool(FerramentaDef def) {
        FunctionParameters.Builder parametros = FunctionParameters.builder();
        LlmSupport.schema(def).forEach((k, v) -> parametros.putAdditionalProperty(k, JsonValue.from(v)));
        return ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(def.nome())
                        .description(def.descricao())
                        .parameters(parametros.build())
                        .build())
                .build();
    }

    private static AssistenteProvedorException traduzir(RuntimeException ex) {
        if (ex instanceof UnauthorizedException || ex instanceof PermissionDeniedException) {
            return LlmSupport.chaveRecusada(ProvedorIa.OPENAI, ex);
        }
        if (ex instanceof OpenAIServiceException service) {
            log.warn("assistente.llm.erro provedor=OPENAI status={} codigo={}", service.statusCode(),
                    service.code().orElse(null));
            return LlmSupport.porStatus(ProvedorIa.OPENAI, service.statusCode(), ex);
        }
        if (ex instanceof OpenAIIoException) {
            return LlmSupport.semConexao(ProvedorIa.OPENAI, ex);
        }
        return LlmSupport.desconhecido(ProvedorIa.OPENAI, ex);
    }

    @Override
    public void destroy() {
        clientes.fecharTodos();
    }
}
