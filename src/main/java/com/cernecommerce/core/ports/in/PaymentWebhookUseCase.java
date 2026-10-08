package com.cernecommerce.core.ports.in;

/**
 * Port de entrada para notificações de pagamento do gateway (ECM-F004, Fatia 10).
 *
 * <p>Separado de {@link ShopUseCase} de propósito: não é superfície de cliente autenticado, é a
 * confirmação assíncrona do gateway — sem sessão, sem principal, sem propriedade a checar.</p>
 */
public interface PaymentWebhookUseCase {

    /**
     * Resultado do processamento — o controller usa para decidir se publica {@code AuditEvent}
     * (só quando algo de fato mudou; a maioria das notificações é no-op: retentativa do gateway,
     * spam, ou pedido que já não estava mais {@code PENDING}). {@code core/service} não pode
     * publicar evento diretamente (ver {@code HexagonalArchitectureTest
     * #core_service_may_only_use_spring_transaction} — só {@code @Transactional} é permitido lá).
     */
    /**
     * {@code paymentDeclined}: o gateway, reconsultado, diz que o pagamento de um pedido conhecido e
     * ainda pendente não foi aprovado — vira o evento {@code PAYMENT_DECLINED} (automações de
     * PAGAMENTO_RECUSADO). Não é falha técnica: essa é {@code PAYMENT_WEBHOOK_FAILED}.
     */
    record WebhookResult(boolean orderPaid, Long orderId, String orderNumber, boolean paymentDeclined) {

        public WebhookResult(boolean orderPaid, Long orderId, String orderNumber) {
            this(orderPaid, orderId, orderNumber, false);
        }

        public static WebhookResult noop() {
            return new WebhookResult(false, null, null, false);
        }

        public static WebhookResult declined(Long orderId, String orderNumber) {
            return new WebhookResult(false, orderId, orderNumber, true);
        }
    }

    /**
     * Processa uma notificação de pagamento. Sempre idempotente e tolerante a dados que não
     * correspondem a nada conhecido — {@code orderNsu} que não resolve a um pedido, ou pedido sem
     * pagamento {@code PENDING} (já processado, ou nunca existiu), termina em no-op silencioso, não
     * em exceção. {@code transactionNsu}/{@code invoiceSlug} nunca decidem nada sozinhos: servem
     * só de chave para reconsultar o gateway (nunca se confia no valor pago do corpo da
     * notificação).
     *
     * @throws com.cernecommerce.core.domain.exception.pagamento.PaymentGatewayException se a
     *         reconsulta ao gateway falhar (rede, timeout, erro do provider) — sinaliza ao
     *         controller que a notificação deve ser tentada de novo mais tarde
     */
    WebhookResult handleNotification(String orderNsu, String transactionNsu, String invoiceSlug);
}
