package com.cernecommerce.core.domain.event;

import java.time.Instant;
import java.util.Map;

/**
 * Evento de auditoria do domínio.
 *
 * <p>Publicado via {@link org.springframework.context.ApplicationEventPublisher} pelos controllers
 * (adapter/in) e consumido por {@code AuditEventListener} na camada de infra.
 * Por ser um record de domínio sem dependências de framework, pode ser publicado
 * por qualquer camada sem quebrar o isolamento da arquitetura hexagonal.</p>
 */
public record AuditEvent(EventType type, String username, Instant timestamp, Map<String, Object> details) {

    public enum EventType {
        // Auth
        USER_LOGGED_IN, USER_LOGGED_OUT, USER_SESSIONS_CLEARED,
        LOGIN_FAILED, ACCOUNT_LOCKED, TOKEN_THEFT_DETECTED, ACCESS_DENIED,
        // User lifecycle
        USER_REGISTERED, USER_EMAIL_VERIFIED,
        USER_CREATED, USER_DELETED, USER_UPDATED, USER_EMAIL_CHANGED,
        USER_ROLE_ASSIGNED, USER_ROLE_REMOVED, USER_ENABLED, USER_DISABLED,
        USER_PASSWORD_CHANGED,
        // Password reset
        PASSWORD_RESET_REQUESTED, PASSWORD_RESET_COMPLETED,
        // Email change
        EMAIL_CHANGE_REQUESTED, EMAIL_CHANGE_CONFIRMED,
        // RBAC management
        ROLE_CREATED, ROLE_DELETED,
        PERMISSION_CREATED, PERMISSION_DELETED,
        PERMISSION_ASSIGNED_TO_ROLE, PERMISSION_REMOVED_FROM_ROLE,
        // 2FA
        TOTP_ENABLED, TOTP_DISABLED, TOTP_BACKUP_CODES_REGENERATED, TOTP_REPLACED,
        // DEV elevation
        DEV_ELEVATION_COMPLETED,
        // OAuth
        OAUTH_GOOGLE_LOGIN, OAUTH_GOOGLE_DISABLED_ATTEMPT,
        // Conta criada ou vinculada ao Google neste login — vira alerta de segurança para o dono.
        OAUTH_GOOGLE_LINKED,
        // Estoque
        PRODUCT_CREATED, WAREHOUSE_CREATED, STOCK_MOVEMENT_REGISTERED, REORDER_POINT_SET,
        // EST-F025 — a conversão entre SKUs é UM ato do operador que produz DOIS movimentos. Um
        // STOCK_MOVEMENT_REGISTERED por ponta descreveria uma saída e uma entrada sem relação
        // aparente, e a trilha perderia justamente o que importa auditar: que foram a mesma decisão.
        // Mesma lição de PDV-C014, que tirou a comanda do EventType emprestado.
        STOCK_CONVERTED,
        // EST-F027 — descartar uma lata pela metade é uma DECISÃO do atendente, e é a única parte
        // da lata que não aparece em stock_movement: a SAIDA da lata nova está lá, o descarte da
        // velha não movimenta nada (a unidade já tinha saído do saldo quando foi aberta).
        OPEN_PACKAGE_REPLACED,
        // COM-F001 — cadastro de fornecedor é dado de compliance (taxId entra em nota fiscal), e
        // ativar/desativar tem evento próprio pelo mesmo motivo de PRODUCT_DEACTIVATED: tirar um
        // fornecedor de circulação é uma decisão, corrigir a razão social dele é uma digitação.
        SUPPLIER_CREATED, SUPPLIER_UPDATED, SUPPLIER_ACTIVATED, SUPPLIER_DEACTIVATED,
        PRODUCT_UPDATED, PRODUCT_ACTIVATED, PRODUCT_DEACTIVATED, PRODUCT_PRICE_CHANGED,
        // EST-F026 — distinto de PRODUCT_DEACTIVATED pela mesma razão que aquele é distinto de
        // PRODUCT_UPDATED: desativar preserva a linha e a trilha, excluir apaga as duas. Só
        // alcançável para rascunho, e é o único evento do módulo cujo objeto não existe mais
        // depois dele — daí gravar o nome junto do SKU, que é tudo o que restará.
        PRODUCT_DELETED,
        // EST-F030 — troca de SKU reescreve a identidade do produto em todo o histórico; o evento
        // guarda antigo e novo, porque depois dele o SKU antigo não aparece em lugar nenhum.
        PRODUCT_SKU_CHANGED,
        // EST-F031 — cadastro do kit montável.
        KIT_TEMPLATE_CREATED, KIT_TEMPLATE_UPDATED, KIT_TEMPLATE_DELETED,
        WAREHOUSE_UPDATED, WAREHOUSE_ACTIVATED, WAREHOUSE_DEACTIVATED,
        STOCK_COUNT_OPENED, STOCK_COUNT_CLOSED, STOCK_COUNT_CANCELLED, KIT_RECIPE_CHANGED,
        PRODUCT_LOT_TRACKED_ENABLED, PRODUCT_LOT_TRACKED_DISABLED,
        PRODUCT_IMAGE_UPLOADED,
        CATEGORY_CREATED, CATEGORY_UPDATED, CATEGORY_ACTIVATED, CATEGORY_DEACTIVATED,
        BRAND_CREATED, BRAND_UPDATED, BRAND_ACTIVATED, BRAND_DEACTIVATED,
        REORDER_POINT_DELETED,
        ATTRIBUTE_TYPE_CREATED,
        REPLENISHMENT_ITEM_ADDED, REPLENISHMENT_ITEM_UPDATED, REPLENISHMENT_ITEM_REMOVED,
        REPLENISHMENT_LIST_CLEARED,
        // CRM
        CUSTOMER_CREATED, CUSTOMER_UPDATED, CUSTOMER_NOTE_ADDED, CUSTOMER_STAGE_CHANGED,
        // Ecommerce (ECM-F001, Fatia 8) — distinto de CUSTOMER_CREATED: nasce de autocadastro
        // público em /shop/register, não de um operador com CRM_CUSTOMER_MANAGE.
        CUSTOMER_MARKETPLACE_REGISTERED,
        TAG_CREATED, TAG_DELETED, CUSTOMER_TAG_ADDED, CUSTOMER_TAG_REMOVED,
        CAMPAIGN_AUTOMATION_CREATED, CAMPAIGN_AUTOMATION_DELETED, CAMPAIGN_AUTOMATION_DISPATCHED,
        CAMPAIGN_AUTOMATION_TOGGLED, CAMPAIGN_AUTOMATION_UPDATED, CAMPAIGN_AUTOMATION_TESTED,
        CUSTOMER_LIST_EXPORTED,
        // PDV — ciclo de caixa
        CASH_SESSION_OPENED, CASH_SESSION_CLOSED, CASH_MOVEMENT_REGISTERED,
        // PDV — comanda de mesa (PDV-C014). Até aqui a comanda inteira andava pendurada em
        // STOCK_MOVEMENT_REGISTERED, discriminada por `origin`. No lançamento e no cancelamento
        // isso era ao menos fiel (o estoque de fato se move); no FECHAMENTO não era — closeComanda
        // não toca em saldo, e o evento entrava na trilha de movimentação de estoque descrevendo
        // algo que não aconteceu. Abrir mesa, por sua vez, não deixava rastro nenhum, ao contrário
        // de abrir caixa. O rastro item a item continua onde sempre esteve: em stock_movement.
        COMANDA_OPENED, COMANDA_CLOSED, COMANDA_CANCELLED,
        COMANDA_ITEM_ADDED, COMANDA_ITEM_REMOVED,
        // PDV-F019 — kit montável lançado/retirado da mesa, como pacote.
        COMANDA_KIT_ADDED, COMANDA_KIT_REMOVED,
        // PDV-F021 — cardápio de sessão da mesa.
        COMANDA_SESSION_ADDED, COMANDA_ROSH_EXTRA_ADDED, SESSION_MENU_CHANGED,
        // PDV-F023
        COMANDA_SESSION_STATUS_CHANGED, COMANDA_FINISHED,
        // PDV-F016. COMANDA_MERGED é o que distingue, na trilha, a origem de uma junção de uma
        // mesa abandonada: as duas terminam CANCELADA, mas só o abandono devolveu estoque.
        COMANDA_RENAMED, COMANDA_MERGED, COMANDA_CUSTOMER_LINKED,
        // PDV-F036 — resposta a "o cliente comprou algo na loja?".
        COMANDA_STORE_PURCHASE_RECORDED,
        // Pedido
        ORDER_STATUS_CHANGED, ORDER_CANCELLED, ORDER_REFUNDED, ORDER_PAYMENT_CORRECTED,
        RECEIVABLE_CREATED, RECEIVABLE_PAID, RECEIVABLE_DUE_DATE_CHANGED, RECEIVABLE_CANCELLED,
        CUSTOMER_CREDIT_LIMIT_CHANGED,
        // PDV-F022 — códigos da 99, rastreio, entregador preenchidos depois da venda.
        ORDER_DELIVERY_UPDATED,
        // Falha ao processar notificação do gateway de pagamento — vira alerta para os devs.
        PAYMENT_WEBHOOK_FAILED,
        // Cashback (CRM-F003)
        CASHBACK_RATE_CHANGED, CASHBACK_EARNED,
        // Support
        BUG_REPORT_CREATED,
        // Integrações da loja (e-mail/Resend)
        INTEGRATION_UPDATED,
        // Financeiro (FIN-F004)
        CASH_FLOW_ENTRY_CREATED, CASH_FLOW_ENTRY_UPDATED, CASH_FLOW_ENTRY_DELETED
    }

    public static AuditEvent of(EventType type, String username) {
        return new AuditEvent(type, username, Instant.now(), Map.of());
    }

    public static AuditEvent of(EventType type, String username, Map<String, Object> details) {
        return new AuditEvent(type, username, Instant.now(), Map.copyOf(details));
    }
}
