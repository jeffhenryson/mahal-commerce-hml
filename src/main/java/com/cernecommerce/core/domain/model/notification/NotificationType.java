package com.cernecommerce.core.domain.model.notification;

public enum NotificationType {
    PASSWORD_CHANGED,
    ACCOUNT_LOCKED,
    TOTP_ENABLED,
    TOTP_DISABLED,
    TOKEN_THEFT_DETECTED,
    EMAIL_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    ACCOUNT_DISABLED,
    SYSTEM,
    ESTOQUE,
    /** Abertura, sangria/suprimento e fechamento de caixa — para quem tem FINANCEIRO_READ. */
    CAIXA,
    /** Cancelamento, reembolso, correção de pagamento e mudança de preço — para quem tem FINANCEIRO_READ. */
    OPERACAO,
    /** Resumo diário de vendas, caixas e fiado vencido — para quem tem FINANCEIRO_READ. */
    RESUMO,
    /** Alertas técnicos (bug report, falha de integração, erro 500, segurança) — para a ROLE_DEV. */
    DEV
}
