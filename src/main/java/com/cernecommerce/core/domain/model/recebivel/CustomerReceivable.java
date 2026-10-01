package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Conta a receber de um cliente VIP — o "marcado" (CRM-F010).
 *
 * <p>Nasce na mesma transação da venda que teve uma linha {@code MARCADO}. {@code amount} é só a
 * parte marcada; {@code items} é o pedido inteiro. {@code amountOpen} é derivado de
 * {@code amount − amountPaid}, e a quitação concorrente é serializada por trava pessimista nos
 * recebíveis do cliente (mais {@code version} como rede).</p>
 */
public record CustomerReceivable(Long id, Long customerId, Long orderId, Long comandaId, Long sessionId,
        BigDecimal amount, BigDecimal amountPaid, LocalDate dueDate, ReceivableStatus status, Instant createdAt,
        String createdBy, Instant settledAt, String cancelReason, String cancelledBy, Instant cancelledAt,
        Long version, List<ReceivableItem> items) {

    public CustomerReceivable {
        if (customerId == null || orderId == null || sessionId == null) {
            throw new IllegalArgumentException("customerId, orderId e sessionId são obrigatórios");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero");
        }
        if (amountPaid == null || amountPaid.signum() < 0 || amountPaid.compareTo(amount) > 0) {
            throw new IllegalArgumentException("amountPaid deve estar entre zero e amount");
        }
        if (dueDate == null || status == null) {
            throw new IllegalArgumentException("dueDate e status são obrigatórios");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static CustomerReceivable open(Long customerId, Long orderId, Long comandaId, Long sessionId,
            BigDecimal amount, LocalDate dueDate, String createdBy, Instant createdAt, List<ReceivableItem> items) {
        return new CustomerReceivable(null, customerId, orderId, comandaId, sessionId, amount, BigDecimal.ZERO,
                dueDate, ReceivableStatus.ABERTO, createdAt, createdBy, null, null, null, null, null, items);
    }

    public BigDecimal amountOpen() {
        return amount.subtract(amountPaid);
    }

    /** Em aberto e com o vencimento antes de {@code today} — vale antes mesmo do job gravar VENCIDO. */
    public boolean isOverdue(LocalDate today) {
        return status.isOpen() && dueDate.isBefore(today);
    }

    /**
     * Abate {@code value} do saldo. Zerou: QUITADO. Sobrou: PARCIAL, ou continua VENCIDO se o prazo
     * já passou — pagar parte não renegocia a data.
     */
    public CustomerReceivable applyPayment(BigDecimal value, Instant at, LocalDate today) {
        if (!status.isOpen()) {
            throw new IllegalStateException("recebível " + id + " não está em aberto: " + status);
        }
        if (value.signum() <= 0 || value.compareTo(amountOpen()) > 0) {
            throw new IllegalArgumentException("valor a abater fora do saldo: " + value);
        }
        BigDecimal paid = amountPaid.add(value);
        boolean settled = paid.compareTo(amount) == 0;
        ReceivableStatus next = settled ? ReceivableStatus.QUITADO
                : dueDate.isBefore(today) ? ReceivableStatus.VENCIDO : ReceivableStatus.PARCIAL;
        return with(paid, dueDate, next, settled ? at : null, null, null, null);
    }

    /** Job diário: em aberto com o prazo vencido passa a VENCIDO. */
    public CustomerReceivable markOverdue() {
        return with(amountPaid, dueDate, ReceivableStatus.VENCIDO, settledAt, cancelReason, cancelledBy, cancelledAt);
    }

    /** Renegociação: novo prazo, e o status volta ao que o pagamento diz (ABERTO ou PARCIAL). */
    public CustomerReceivable withDueDate(LocalDate newDueDate) {
        if (!status.isOpen()) {
            throw new IllegalStateException("recebível " + id + " não está em aberto: " + status);
        }
        ReceivableStatus next = amountPaid.signum() > 0 ? ReceivableStatus.PARCIAL : ReceivableStatus.ABERTO;
        return with(amountPaid, newDueDate, next, settledAt, cancelReason, cancelledBy, cancelledAt);
    }

    public CustomerReceivable cancelled(String reason, String by, Instant at) {
        if (!status.isOpen()) {
            throw new IllegalStateException("recebível " + id + " não está em aberto: " + status);
        }
        return with(amountPaid, dueDate, ReceivableStatus.CANCELADO, null, reason, by, at);
    }

    public CustomerReceivable withItems(List<ReceivableItem> newItems) {
        return new CustomerReceivable(id, customerId, orderId, comandaId, sessionId, amount, amountPaid, dueDate,
                status, createdAt, createdBy, settledAt, cancelReason, cancelledBy, cancelledAt, version, newItems);
    }

    private CustomerReceivable with(BigDecimal newPaid, LocalDate newDueDate, ReceivableStatus newStatus,
            Instant newSettledAt, String newCancelReason, String newCancelledBy, Instant newCancelledAt) {
        return new CustomerReceivable(id, customerId, orderId, comandaId, sessionId, amount, newPaid, newDueDate,
                newStatus, createdAt, createdBy, newSettledAt, newCancelReason, newCancelledBy, newCancelledAt,
                version, items);
    }
}
