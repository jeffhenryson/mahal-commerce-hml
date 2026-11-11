package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.assistente.AssistenteAcoes;
import com.cernecommerce.adapter.in.assistente.AssistenteMapper;
import com.cernecommerce.adapter.in.assistente.AssistenteOrquestrador;
import com.cernecommerce.adapter.in.assistente.PiiSanitizer;
import com.cernecommerce.adapter.in.dtos.request.AssistenteFeedbackRequest;
import com.cernecommerce.adapter.in.dtos.request.AssistenteMensagemRequest;
import com.cernecommerce.adapter.in.dtos.request.AssistenteRenomearRequest;
import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoResultadoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteConversaDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteConversaResumoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteStatusDTO;
import com.cernecommerce.core.domain.exception.assistente.AcaoJaExecutadaException;
import com.cernecommerce.core.domain.exception.assistente.AssistenteNaoConfiguradoException;
import com.cernecommerce.core.domain.exception.assistente.AssistenteOcupadoException;
import com.cernecommerce.core.domain.exception.assistente.ConversaNaoEncontradaException;
import com.cernecommerce.core.domain.exception.assistente.SubstituicaoInvalidaException;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.in.AssistenteConversaUseCase;
import com.cernecommerce.core.ports.out.ratelimit.ResourceRateLimiterPort;
import com.cernecommerce.infra.handler.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Assistente de IA do admin (chat com Claude). Sem {@code module.assistente.enabled}, o controller
 * não existe e {@code GET /assistente/status} responde 404 — o front esconde o bubble.
 */
@RestController
@RequestMapping("/assistente")
@ConditionalOnProperty(name = "module.assistente.enabled", havingValue = "true", matchIfMissing = true)
@Tag(name = "Assistente de IA", description = "Chat do admin com ferramentas curadas e ações confirmadas pelo usuário")
public class AssistenteController {

    private static final Logger log = LoggerFactory.getLogger(AssistenteController.class);
    static final String BUCKET = "assistente-mensagens";
    private static final int TEXTO_MAX = 4000;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AssistenteConversaUseCase conversas;
    private final AssistantIntegrationUseCase integracao;
    private final AssistenteOrquestrador orquestrador;
    private final AssistenteAcoes acoes;
    private final ResourceRateLimiterPort rateLimiter;

    public AssistenteController(AssistenteConversaUseCase conversas, AssistantIntegrationUseCase integracao,
            AssistenteOrquestrador orquestrador, AssistenteAcoes acoes, ResourceRateLimiterPort rateLimiter) {
        this.conversas = conversas;
        this.integracao = integracao;
        this.orquestrador = orquestrador;
        this.acoes = acoes;
        this.rateLimiter = rateLimiter;
    }

    @Operation(summary = "Estado do assistente",
            description = "habilitado = o módulo existe neste deploy; configurado = a loja salvou a chave e ativou.")
    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<AssistenteStatusDTO> status() {
        return ResponseEntity.ok(new AssistenteStatusDTO(true, integracao.activeCredentials().isPresent()));
    }

    @Operation(summary = "Conversas do usuário logado", description = "As 50 mais recentes.")
    @GetMapping("/conversas")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<List<AssistenteConversaResumoDTO>> listar(Authentication auth) {
        return ResponseEntity.ok(conversas.listar(auth.getName()).stream().map(AssistenteMapper::resumo).toList());
    }

    @Operation(summary = "Cria uma conversa vazia")
    @PostMapping("/conversas")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<AssistenteConversaResumoDTO> criar(Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(AssistenteMapper.resumo(conversas.criar(auth.getName())));
    }

    @Operation(summary = "Conversa com as mensagens",
            description = "turnoEmAndamento = o servidor ainda está escrevendo uma resposta (o stream pode ter "
                    + "caído; releia em instantes). Conversa de outro usuário: 404.")
    @GetMapping("/conversas/{id}")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<AssistenteConversaDTO> obter(@PathVariable String id, Authentication auth) {
        return ResponseEntity.ok(AssistenteMapper.detalhe(conversas.obter(id, auth.getName()), Instant.now(),
                orquestrador.emAndamento(id)));
    }

    @Operation(summary = "Para a resposta em andamento (botão Parar)",
            description = "O turno para no próximo ponto seguro e grava o que já tinha. Fechar a conexão não "
                    + "cancela: o servidor termina e grava a resposta. Idempotente: sem turno em andamento, 204 "
                    + "do mesmo jeito. Conversa de outro usuário: 404.")
    @PostMapping("/conversas/{id}/cancelar")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<Void> cancelar(@PathVariable String id, Authentication auth) {
        orquestrador.cancelar(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Renomeia uma conversa",
            description = "Até 80 caracteres; espaços repetidos viram um só e dado pessoal é removido. Não muda "
                    + "atualizadaEm (a conversa não sobe no histórico). Vazio ou longo demais: 400 "
                    + "VALIDATION_ERROR. Conversa de outro usuário: 404.")
    @PatchMapping("/conversas/{id}")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<AssistenteConversaResumoDTO> renomear(@PathVariable String id,
            @Valid @RequestBody AssistenteRenomearRequest body, Authentication auth) {
        return ResponseEntity.ok(AssistenteMapper.resumo(
                conversas.renomear(id, auth.getName(), PiiSanitizer.limpar(body.getTitulo()))));
    }

    @Operation(summary = "Avalia uma resposta do assistente (👍/👎)",
            description = "nota: POSITIVO, NEGATIVO ou null (limpa). motivo opcional, até 500 caracteres, sem dado "
                    + "pessoal. Só mensagens do assistente; mensagem do usuário ou de outro usuário: 404. Não muda "
                    + "atualizadaEm da conversa.")
    @PutMapping("/mensagens/{id}/feedback")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<Void> avaliar(@PathVariable String id, @Valid @RequestBody AssistenteFeedbackRequest body,
            Authentication auth) {
        String motivo = body.getNota() == null || body.getMotivo() == null ? null : PiiSanitizer.limpar(body.getMotivo());
        Mensagem m = conversas.avaliar(id, auth.getName(), body.getNota(), motivo);
        // Sem o motivo: o log serve para comparar provedores, não para ler o que o usuário escreveu.
        log.info("assistente.feedback nota={} provedor={} modelo={}", body.getNota(), m.provedor(), m.modelo());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Apaga uma conversa", description = "Conversa de outro usuário: 404.")
    @DeleteMapping("/conversas/{id}")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<Void> excluir(@PathVariable String id, Authentication auth) {
        conversas.excluir(id, auth.getName());
        return ResponseEntity.noContent().build();
    }

    /**
     * Envia uma mensagem e responde {@code text/event-stream}. Os erros anteriores ao stream (403,
     * 404, 409, 429) são escritos à mão como JSON: com {@code Accept: text/event-stream} o handler
     * global não consegue serializar o {@code ApiError}. Pelo mesmo motivo a permissão é conferida
     * aqui, não no {@code @PreAuthorize} — e recusar depois de abrir o emitter viraria 401 pelo
     * re-dispatch assíncrono (ver {@code SseEmitterRegistry}).
     */
    @Operation(summary = "Envia uma mensagem ao assistente (SSE)",
            description = "Eventos: titulo (só no 1º turno, antes do texto), texto, ferramenta, etapa {id, rotulo, "
                    + "estado: inicio|fim|falha}, acao, bloco e link (depois do último texto), fim {mensagemId, "
                    + "perguntaId, provedor, modelo} e erro {codigo, mensagem, perguntaId?}. Comentário ': ping' "
                    + "a cada ~15 s mantém a conexão viva. Se a conexão cair, o turno continua e grava a "
                    + "resposta; para parar, POST /conversas/{id}/cancelar. "
                    + "substituirDesde (id da última pergunta) refaz o turno: apaga essa pergunta e o que veio "
                    + "depois, e as ações PENDENTE delas expiram. Antes de abrir o stream: 403 sem ASSISTENTE_USE, "
                    + "404 conversa inexistente ou de outro usuário, 409 ASSISTENTE_NAO_CONFIGURADO, "
                    + "ASSISTENTE_OCUPADO, SUBSTITUICAO_INVALIDA (não é a última pergunta) ou ACAO_JA_EXECUTADA "
                    + "(a resposta já executou uma alteração), 429 com Retry-After (20 mensagens a cada 5 min; "
                    + "refazer conta). Falha do provedor depois de aberto vira evento erro com codigo=PROVEDOR.")
    @PostMapping("/conversas/{id}/mensagens")
    @PreAuthorize("isAuthenticated()")
    public SseEmitter enviar(@PathVariable String id, @RequestBody AssistenteMensagemRequest body,
            Authentication auth, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (auth.getAuthorities().stream().noneMatch(a -> "ASSISTENTE_USE".equals(a.getAuthority()))) {
            return erro(response, request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Acesso negado", null);
        }
        String texto = body == null ? null : body.getTexto();
        if (texto == null || texto.isBlank() || texto.length() > TEXTO_MAX) {
            return erro(response, request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Envie um texto de 1 a " + TEXTO_MAX + " caracteres", null);
        }
        String substituirDesde = body.getSubstituirDesde();
        if (substituirDesde != null && substituirDesde.length() > 36) {
            return erro(response, request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "substituirDesde deve ser o id de uma mensagem", null);
        }
        try {
            // A conversa e a configuração são conferidas de novo no orquestrador; aqui só para o 404/409
            // não gastar uma mensagem do limite.
            conversas.exigir(id, auth.getName());
            rateLimiter.checkOrThrow(BUCKET, auth.getName());
            AssistenteMensagemRequest.Contexto ctx = body.getContexto();
            // Nada de buffer nem compressão entre a API e o navegador (Traefik, Cloudflare, nginx).
            response.setHeader("Cache-Control", "no-cache, no-transform");
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("Connection", "keep-alive");
            return orquestrador.iniciarTurno(id, auth.getName(), texto,
                    ctx == null ? null : ctx.getRota(), ctx == null ? null : ctx.getTitulo(), substituirDesde,
                    request.getRemoteAddr(), userAgent(request));
        } catch (ConversaNaoEncontradaException ex) {
            return erro(response, request, HttpStatus.NOT_FOUND, "CONVERSA_NAO_ENCONTRADA", ex.getMessage(), null);
        } catch (AssistenteNaoConfiguradoException ex) {
            return erro(response, request, HttpStatus.CONFLICT, "ASSISTENTE_NAO_CONFIGURADO", ex.getMessage(), null);
        } catch (AssistenteOcupadoException ex) {
            return erro(response, request, HttpStatus.CONFLICT, "ASSISTENTE_OCUPADO", ex.getMessage(), null);
        } catch (SubstituicaoInvalidaException ex) {
            return erro(response, request, HttpStatus.CONFLICT, "SUBSTITUICAO_INVALIDA", ex.getMessage(), null);
        } catch (AcaoJaExecutadaException ex) {
            return erro(response, request, HttpStatus.CONFLICT, "ACAO_JA_EXECUTADA", ex.getMessage(), null);
        } catch (RateLimitExceededException ex) {
            return erro(response, request, HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                    "Muitas mensagens em pouco tempo. Aguarde um pouco.", ex.retryAfterSeconds());
        }
    }

    @Operation(summary = "Confirma uma ação proposta pelo assistente",
            description = "Executa exatamente o que foi guardado, uma vez. Repetir devolve o mesmo resultado. "
                    + "Vencida: 409 ACAO_EXPIRADA. Outro usuário: 404.")
    @PostMapping("/acoes/{acaoId}/confirmar")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<AssistenteAcaoResultadoDTO> confirmar(@PathVariable String acaoId, Authentication auth,
            HttpServletRequest request) {
        return ResponseEntity.ok(acoes.confirmar(acaoId, auth.getName(), request.getRemoteAddr(), userAgent(request)));
    }

    @Operation(summary = "Recusa uma ação proposta pelo assistente", description = "Idempotente. Outro usuário: 404.")
    @PostMapping("/acoes/{acaoId}/recusar")
    @PreAuthorize("hasAuthority('ASSISTENTE_USE')")
    public ResponseEntity<AssistenteAcaoResultadoDTO> recusar(@PathVariable String acaoId, Authentication auth) {
        return ResponseEntity.ok(acoes.recusar(acaoId, auth.getName()));
    }

    private static SseEmitter erro(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
            String codigo, String mensagem, Long retryAfter) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (retryAfter != null) {
            response.setHeader("Retry-After", String.valueOf(retryAfter));
        }
        MAPPER.writeValue(response.getOutputStream(),
                ApiError.of(mensagem, codigo, request.getRequestURI(), MDC.get("traceId")));
        return null;
    }

    private static String userAgent(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) {
            return null;
        }
        return ua.length() > 512 ? ua.substring(0, 512) : ua;
    }
}
