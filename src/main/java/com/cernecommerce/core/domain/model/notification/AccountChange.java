package com.cernecommerce.core.domain.model.notification;

/** O que a loja alterou na conta de alguém: cada valor tem um e-mail próprio. */
public enum AccountChange {
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    DISABLED
}
