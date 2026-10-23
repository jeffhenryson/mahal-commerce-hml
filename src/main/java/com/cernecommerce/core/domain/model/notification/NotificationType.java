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
    /** Sessão pedida pelo QR da mesa (autoatendimento do Salão) — para quem tem PDV_COMANDA_MANAGE. */
    SOLICITACAO_SESSAO,
    /** Chamado da mesa pelo QR (conta, recolher, carvão, atendente) — para quem tem PDV_COMANDA_MANAGE. */
    CHAMADO_MESA,
    /** Pedido novo ou cancelado pelo app do cliente (ECM-F010) — para quem tem PDV_SALE_MANAGE. */
    PEDIDO_APP,
    /** Alertas técnicos (bug report, falha de integração, erro 500, segurança) — para a ROLE_DEV. */
    DEV
}
