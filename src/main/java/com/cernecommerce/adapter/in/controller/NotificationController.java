package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.response.NotificationResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SseTicketResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.sse.SseTicketPort;
import com.cernecommerce.adapter.in.sse.SseEmitterRegistry;
import com.cernecommerce.infra.handler.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class NotificationController {

    /**
     * Vida do bilhete de SSE. Curta de propósito: ele só precisa sobreviver ao intervalo entre a
     * resposta do POST e a abertura do EventSource, que é uma ida e volta na mesma página.
     */
    static final long TICKET_TTL_SECONDS = 30;

    /** Quanto o front espera para tentar de novo quando o usuário está no limite de streams. */
    static final long SSE_RETRY_AFTER_SECONDS = 30;

    // Mapper próprio pelo mesmo motivo do RestAuthenticationEntryPoint: a resposta de erro do stream
    // é escrita à mão, fora da negociação de conteúdo (o endpoint só produz text/event-stream).
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final NotificationUseCase useCase;
    private final SseEmitterRegistry sseRegistry;
    private final SseTicketPort sseTickets;

    public NotificationController(NotificationUseCase useCase, SseEmitterRegistry sseRegistry,
            SseTicketPort sseTickets) {
        this.useCase = useCase;
        this.sseRegistry = sseRegistry;
        this.sseTickets = sseTickets;
    }

    @Operation(summary = "Lista notificações do usuário autenticado")
    @GetMapping
    public ResponseEntity<PageResult<NotificationResponseDTO>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        PageResult<Notification> result = useCase.getNotifications(auth.getName(), unreadOnly, page, size);
        PageResult<NotificationResponseDTO> response = new PageResult<>(
                result.content().stream().map(NotificationResponseDTO::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Retorna quantidade de notificações não lidas")
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication auth) {
        long count = useCase.countUnread(auth.getName());
        return ResponseEntity.ok(Map.of("count", count));
    }

    @Operation(summary = "Marca notificação específica como lida")
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id, Authentication auth) {
        useCase.markAsRead(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Marca todas as notificações como lidas")
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication auth) {
        useCase.markAllAsRead(auth.getName());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove uma notificação do usuário autenticado")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication auth) {
        useCase.delete(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Emite um bilhete de uso único para abrir o stream SSE (PLAT-C051)",
            description = "Chamado com o `Authorization: Bearer` normal, devolve um valor opaco "
                    + "para ser passado como `?ticket=` em `GET /notifications/stream`.\n\n"
                    + "Existe porque a API `EventSource` do navegador **não envia headers** — sem "
                    + "isto o stream respondia 401 para todo cliente de navegador, e a notificação "
                    + "em tempo real não existia. O bilhete não substitui o Bearer: quem consegue "
                    + "mandar o header (curl, integração servidor-a-servidor) continua abrindo o "
                    + "stream direto.\n\n"
                    + "**Não é um token de acesso.** Vale para uma rota, um uso e "
                    + TICKET_TTL_SECONDS + " segundos — de propósito, porque query string entra em "
                    + "log de acesso, histórico de proxy e cabeçalho `Referer`, e o access token "
                    + "vale 15 minutos em toda a API.")
    @PostMapping("/stream-ticket")
    public ResponseEntity<SseTicketResponseDTO> issueStreamTicket(Authentication auth) {
        SseTicketResponseDTO response = new SseTicketResponseDTO();
        response.setTicket(sseTickets.issue(auth.getName(), TICKET_TTL_SECONDS));
        response.setExpiresInSeconds(TICKET_TTL_SECONDS);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Stream SSE de notificações em tempo real",
            description = "Aceita `Authorization: Bearer` **ou** `?ticket=` emitido por "
                    + "`POST /notifications/stream-ticket` — o bilhete é o caminho do navegador, "
                    + "cujo `EventSource` não envia headers (PLAT-C051). O bilhete é consumido na "
                    + "abertura: cada reconexão precisa de um novo.\n\n"
                    + "Com o usuário já no limite de streams abertos (`notification.sse.max-connections-per-user`, "
                    + "padrão 10), responde **429** `SSE_CONNECTION_LIMIT` com `Retry-After`: espere e reconecte, "
                    + "a sessão continua válida.")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if (sseRegistry.limiteAtingido(auth.getName())) {
            ApiError error = ApiError.of("Limite de conexões em tempo real atingido — feche outras abas ou aguarde",
                    "SSE_CONNECTION_LIMIT", request.getRequestURI(), MDC.get("traceId"));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(SSE_RETRY_AFTER_SECONDS));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            MAPPER.writeValue(response.getWriter(), error);
            // Nulo: o Spring dá a requisição por respondida e não abre o stream.
            return null;
        }
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
        sseRegistry.register(auth.getName(), emitter);
        return emitter;
    }
}
