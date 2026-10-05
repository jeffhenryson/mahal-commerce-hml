package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.PaymentWebhookRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase.WebhookResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Notificação de pagamento do gateway (ECM-F004, Fatia 10) — hoje só InfinitePay. Público por
 * natureza: é o próprio gateway chamando, sem sessão de usuário nenhuma — ver exceção documentada
 * em {@code SecurityArchitectureTest} e a regra em {@code SecurityConfig}.
 *
 * <p>Sem assinatura de webhook (o InfinitePay não oferece uma) — a defesa mora inteira dentro de
 * {@link PaymentWebhookUseCase#handleNotification}, nunca aqui: esta camada só traduz protocolo.</p>
 */
@RestController
@RequestMapping("/webhooks/payments")
@Tag(name = "Webhook de pagamento", description = "Notificação assíncrona do gateway — público, sem autenticação")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);
    private static final String WEBHOOK_ACTOR = "webhook:infinitepay";

    private final PaymentWebhookUseCase paymentWebhookUseCase;
    private final ApplicationEventPublisher publisher;

    public PaymentWebhookController(PaymentWebhookUseCase paymentWebhookUseCase, ApplicationEventPublisher publisher) {
        this.paymentWebhookUseCase = paymentWebhookUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Notificação de pagamento do gateway",
            description = "200 sempre que a notificação foi processada (mesmo sem efeito — idempotente). "
                    + "400 é o gatilho de nova tentativa do InfinitePay: usado para qualquer falha ao "
                    + "processar, para que o gateway tente de novo mais tarde.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Processada (com ou sem efeito)"),
            @ApiResponse(responseCode = "400", description = "Falha ao processar — tente novamente", content = @Content)
    })
    @PostMapping("/{provider}")
    public ResponseEntity<Void> receive(@PathVariable String provider, @RequestBody PaymentWebhookRequest request) {
        try {
            WebhookResult result = paymentWebhookUseCase.handleNotification(
                    request.getOrderNsu(), request.getTransactionNsu(), request.getInvoiceSlug());
            if (result.orderPaid()) {
                publisher.publishEvent(AuditEvent.of(EventType.ORDER_STATUS_CHANGED, WEBHOOK_ACTOR, Map.of(
                        "orderId", result.orderId(),
                        "orderNumber", String.valueOf(result.orderNumber()),
                        "provider", provider,
                        "to", "PAGO")));
            }
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            // Qualquer falha ao processar vira 400 — é o gatilho de nova tentativa documentado do
            // InfinitePay. Deixar cair no handler genérico devolveria 500/502, que não é o que o
            // gateway reconhece como "tente de novo".
            log.error("payment.webhook.processing_failed provider={} orderNsu={}", provider,
                    request.getOrderNsu(), ex);
            // O gateway tenta de novo, mas pagamento parado sem ninguém saber é pedido que não anda:
            // o evento fica na trilha e vira alerta para os devs (OperationalNotificationListener).
            publisher.publishEvent(AuditEvent.of(EventType.PAYMENT_WEBHOOK_FAILED, WEBHOOK_ACTOR, Map.of(
                    "provider", provider,
                    "orderNsu", String.valueOf(request.getOrderNsu()),
                    "exception", ex.getClass().getName(),
                    "message", String.valueOf(ex.getMessage()))));
            return ResponseEntity.badRequest().build();
        }
    }
}
