package com.cernecommerce.core.domain.exception.user;

/**
 * PLAT-F005 — o cliente do CRM já está ligado a outra conta de usuário. Um cliente tem no máximo uma
 * conta (o índice único {@code uk_users_customer_id} garante o mesmo no banco).
 */
public class CustomerAlreadyLinkedException extends RuntimeException {
    public CustomerAlreadyLinkedException(Long customerId, String username) {
        super("Este cliente já está vinculado ao usuário " + username + " (cliente " + customerId + ").");
    }
}
