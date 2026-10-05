package com.cernecommerce.core.domain.model.config;

/** Os e-mails transacionais do sistema — usado para disparar um exemplo de cada no teste. */
public enum EmailSample {
    VERIFICATION_CODE,
    PASSWORD_RESET,
    EMAIL_CHANGE,
    WELCOME,
    ACCOUNT_CHANGE,
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED,
    TOTP_STATUS,
    TOKEN_THEFT,
    ORDER_CONFIRMATION,
    ORDER_STATUS_UPDATE,
    ORDER_CANCELLATION,
    PURCHASE_RECEIPT,
    RECEIVABLE_REMINDER,
    CASH_SESSION_CLOSED,
    CASH_SESSION_STALE,
    DAILY_DIGEST,
    STOCK_REORDER_ALERT,
    DEV_ALERT
}
