package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.ProductRequestCustomerRequiredException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestValidationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma solicitação dentro do pedido de cliente: quem pediu (cliente do CRM ou avulso com nome e telefone),
 * quanto, até quanto paga e com que urgência. Cancelar não apaga: a linha fica {@code CANCELLED}.
 */
public record ProductRequestEntry(
        Long id,
        Long requestId,
        Long customerId,
        String customerName,
        String customerPhone,
        int quantity,
        BigDecimal maxPrice,
        ProductRequestUrgency urgency,
        LocalDate neededBy,
        String note,
        ProductRequestEntryStatus status,
        Instant notifiedAt,
        ProductRequestNotifyChannel notifiedChannel,
        String createdBy,
        Instant createdAt) {

    public ProductRequestEntry {
        customerName = blankToNull(customerName);
        customerPhone = digitsOrNull(customerPhone);
        note = blankToNull(note);
        if (customerId == null && (customerName == null || customerPhone == null)) {
            throw new ProductRequestCustomerRequiredException();
        }
        if (customerPhone != null && (customerPhone.length() < 10 || customerPhone.length() > 13)) {
            throw new ProductRequestValidationException("Telefone deve ter de 10 a 13 dígitos");
        }
        if (quantity < 1) {
            throw new ProductRequestValidationException("A quantidade mínima é 1");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new ProductRequestValidationException("O preço máximo não pode ser negativo");
        }
        if (urgency == null) {
            urgency = ProductRequestUrgency.MEDIUM;
        }
        if (status == null) {
            status = ProductRequestEntryStatus.ACTIVE;
        }
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy é obrigatório");
        }
    }

    /**
     * Nova solicitação. Cliente do CRM não guarda nome/telefone avulsos: eles vêm do cadastro na leitura,
     * e assim a anonimização (V206) vale também aqui.
     */
    public static ProductRequestEntry create(Long requestId, Long customerId, String customerName,
            String customerPhone, int quantity, BigDecimal maxPrice, ProductRequestUrgency urgency,
            LocalDate neededBy, String note, String createdBy, Instant now) {
        boolean registered = customerId != null;
        return new ProductRequestEntry(null, requestId, customerId, registered ? null : customerName,
                registered ? null : customerPhone, quantity, maxPrice, urgency, neededBy, note,
                ProductRequestEntryStatus.ACTIVE, null, null, createdBy, now);
    }

    public boolean isActive() {
        return status == ProductRequestEntryStatus.ACTIVE;
    }

    public boolean isRegistered() {
        return customerId != null;
    }

    /** Ativa e ainda sem aviso: entra em {@code pendingNotifications}. */
    public boolean awaitsNotification() {
        return isActive() && notifiedAt == null;
    }

    /** Mesmo cliente: pelo id do CRM, ou pelo telefone quando os dois são avulsos. */
    public boolean sameCustomerAs(ProductRequestEntry other) {
        if (customerId != null || other.customerId != null) {
            return Objects.equals(customerId, other.customerId);
        }
        return customerPhone != null && customerPhone.equals(other.customerPhone);
    }

    /**
     * O mesmo cliente pediu de novo: soma a quantidade e atualiza o que veio preenchido, sem duplicar a linha.
     */
    public ProductRequestEntry mergeWith(ProductRequestEntry again) {
        return new ProductRequestEntry(id, requestId, customerId,
                again.customerName != null ? again.customerName : customerName, customerPhone,
                quantity + again.quantity, again.maxPrice != null ? again.maxPrice : maxPrice,
                again.urgency != null ? again.urgency : urgency,
                again.neededBy != null ? again.neededBy : neededBy, again.note != null ? again.note : note,
                status, notifiedAt, notifiedChannel, createdBy, createdAt);
    }

    /** PATCH da entrada: {@code null} mantém o valor. */
    public ProductRequestEntry patch(Integer newQuantity, BigDecimal newMaxPrice, ProductRequestUrgency newUrgency,
            LocalDate newNeededBy, String newNote) {
        return new ProductRequestEntry(id, requestId, customerId, customerName, customerPhone,
                newQuantity != null ? newQuantity : quantity, newMaxPrice != null ? newMaxPrice : maxPrice,
                newUrgency != null ? newUrgency : urgency, newNeededBy != null ? newNeededBy : neededBy,
                newNote != null ? newNote : note, status, notifiedAt, notifiedChannel, createdBy, createdAt);
    }

    public ProductRequestEntry withStatus(ProductRequestEntryStatus newStatus) {
        return new ProductRequestEntry(id, requestId, customerId, customerName, customerPhone, quantity, maxPrice,
                urgency, neededBy, note, newStatus, notifiedAt, notifiedChannel, createdBy, createdAt);
    }

    public ProductRequestEntry notified(ProductRequestNotifyChannel channel, Instant at) {
        return new ProductRequestEntry(id, requestId, customerId, customerName, customerPhone, quantity, maxPrice,
                urgency, neededBy, note, status, at, channel, createdBy, createdAt);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String digitsOrNull(String s) {
        if (s == null) {
            return null;
        }
        String digits = s.replaceAll("\\D", "");
        return digits.isEmpty() ? null : digits;
    }
}
