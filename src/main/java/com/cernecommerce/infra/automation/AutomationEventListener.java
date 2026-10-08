package com.cernecommerce.infra.automation;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Gatilhos automáticos das automações do CRM a partir do barramento de {@link AuditEvent}: traduz
 * o tipo do evento para {@link AutomationEvent} (ou entrada de estágio) e entrega ao
 * {@link AutomationDispatchUseCase}, o ponto único de entrega.
 *
 * <p>Os {@code AuditEvent}s são publicados pelos controllers depois que o caso de uso retornou —
 * a transação já fez commit, então não há como disparar uma venda que sofreu rollback. Os que
 * nascem dentro de serviço ({@code ORDER_CREATED}, {@code STOCK_BELOW_REORDER_POINT}) saem pelo
 * {@code TransactionAfterCommitExecutor}, pelo mesmo motivo.</p>
 *
 * <p>Assíncrono como o {@code OperationalNotificationListener}: a entrega faz chamadas HTTP e não
 * pode atrasar a resposta ao operador. A chave de cada ocorrência ({@code key}) é o que torna o
 * disparo idempotente.</p>
 */
@Component
public class AutomationEventListener {

    private static final Logger log = LoggerFactory.getLogger(AutomationEventListener.class);
    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");
    /** Estados finais de entrega/retirada — {@code PAGO} tem evento próprio ({@code PAYMENT_APPROVED}). */
    private static final Set<String> CONCLUDED_ORDER_STATUSES = Set.of("ENTREGUE", "CONCLUIDO");
    private static final Set<String> CASH_CONTEXT_KEYS = Set.of("sessionId", "expectedAmount", "countedAmount",
            "differenceAmount", "diverges", "operator");

    private final AutomationDispatchUseCase dispatcher;

    public AutomationEventListener(AutomationDispatchUseCase dispatcher) {
        this.dispatcher = dispatcher;
    }

    @EventListener
    @Async("taskExecutor")
    public void onAuditEvent(AuditEvent event) {
        try {
            toOccurrence(event).ifPresent(dispatcher::dispatch);
        } catch (Exception ex) {
            log.error("automation.event.failed type={} error={}", event.type(), ex.getMessage(), ex);
        }
    }

    /** Ocorrência de automação que o evento representa; vazio quando o evento não dispara automações. */
    static Optional<AutomationOccurrence> toOccurrence(AuditEvent event) {
        Map<String, Object> d = event.details();
        String day = LocalDate.ofInstant(event.timestamp(), ZONA_BRASIL).toString();
        return switch (event.type()) {
            case CUSTOMER_STAGE_CHANGED -> {
                Long customerId = asLong(d.get("customerId"));
                CustomerStage estagio = stage(d.get("estagio"));
                yield customerId == null || estagio == null ? Optional.empty()
                        : Optional.of(AutomationOccurrence.stageEntered(customerId, estagio,
                                "STAGE:" + estagio + ":" + day));
            }
            case CUSTOMER_CREATED, CUSTOMER_MARKETPLACE_REGISTERED -> withCustomer(d, customerId ->
                    AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, customerId, null,
                            "CLIENTE:" + customerId, null));
            case ORDER_CREATED -> withOrder(d, orderId ->
                    AutomationOccurrence.event(AutomationEvent.PEDIDO_CRIADO, null, orderId, "PEDIDO:" + orderId, null));
            case ORDER_STATUS_CHANGED -> CONCLUDED_ORDER_STATUSES.contains(String.valueOf(d.get("to")))
                    ? withOrder(d, orderId -> AutomationOccurrence.event(AutomationEvent.PEDIDO_CONCLUIDO, null,
                            orderId, "PEDIDO:" + orderId, null))
                    : Optional.empty();
            case ORDER_CANCELLED, ORDER_REFUNDED -> withOrder(d, orderId ->
                    AutomationOccurrence.event(AutomationEvent.PEDIDO_CANCELADO, null, orderId,
                            "PEDIDO:" + orderId, Map.of("tipo", event.type().name())));
            case PAYMENT_APPROVED -> withOrder(d, orderId ->
                    AutomationOccurrence.event(AutomationEvent.PAGAMENTO_APROVADO, null, orderId,
                            "PAGTO:" + orderId + ":OK", null));
            case PAYMENT_DECLINED -> withOrder(d, orderId ->
                    AutomationOccurrence.event(AutomationEvent.PAGAMENTO_RECUSADO, null, orderId,
                            "PAGTO:" + orderId + ":NOK:" + d.getOrDefault("transactionNsu", day), null));
            case PDV_SALE_COMPLETED -> withOrder(d, orderId ->
                    AutomationOccurrence.event(AutomationEvent.VENDA_PDV_CONCLUIDA, asLong(d.get("customerId")),
                            orderId, "VENDA:" + orderId, null));
            // Um disparo por pedido que cobrou a mesa (o fechamento parcial gera um pedido por vez).
            // O FINISHED aponta o último pedido que cobrou a mesa — mesma chave, não repete. Sem
            // cliente na mesa não há para quem enviar: o dispatcher descarta.
            case COMANDA_CLOSED, COMANDA_FINISHED -> {
                Long comandaId = asLong(d.get("comandaId"));
                Long orderId = asLong(d.get("orderId"));
                yield comandaId == null ? Optional.empty()
                        : Optional.of(AutomationOccurrence.event(AutomationEvent.COMANDA_FECHADA,
                                asLong(d.get("customerId")), orderId,
                                "COMANDA:" + comandaId + ":" + (orderId == null ? "-" : orderId), null));
            }
            case CASH_SESSION_CLOSED -> {
                Long sessionId = asLong(d.get("sessionId"));
                yield sessionId == null ? Optional.empty()
                        : Optional.of(AutomationOccurrence.event(AutomationEvent.CAIXA_FECHADO, null, null,
                                "CAIXA:" + sessionId, subset(d, CASH_CONTEXT_KEYS)));
            }
            case STOCK_BELOW_REORDER_POINT -> {
                Object sku = d.get("sku");
                // Por dia: a regra de reposição olha o nível, não o cruzamento — sem o dia, cada
                // movimento abaixo do mínimo dispararia de novo.
                yield sku == null ? Optional.empty()
                        : Optional.of(AutomationOccurrence.event(AutomationEvent.ESTOQUE_BAIXO, null, null,
                                "ESTOQUE:" + sku + ":" + d.getOrDefault("warehouseCode", "-") + ":" + day, d));
            }
            default -> Optional.empty();
        };
    }

    private static Optional<AutomationOccurrence> withCustomer(Map<String, Object> d,
            java.util.function.Function<Long, AutomationOccurrence> factory) {
        Long customerId = asLong(d.get("customerId"));
        return customerId == null ? Optional.empty() : Optional.of(factory.apply(customerId));
    }

    private static Optional<AutomationOccurrence> withOrder(Map<String, Object> d,
            java.util.function.Function<Long, AutomationOccurrence> factory) {
        Long orderId = asLong(d.get("orderId"));
        return orderId == null ? Optional.empty() : Optional.of(factory.apply(orderId));
    }

    private static Map<String, Object> subset(Map<String, Object> d, Set<String> keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        d.forEach((k, v) -> {
            if (keys.contains(k) && v != null) {
                out.put(k, v);
            }
        });
        return out;
    }

    /** Os {@code details} trazem ids como Long ou String, conforme quem publicou. */
    static Long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.valueOf(s.strip());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static CustomerStage stage(Object value) {
        try {
            return value == null ? null : CustomerStage.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
