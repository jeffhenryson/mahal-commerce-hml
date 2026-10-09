package com.cernecommerce.infra.config;

import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Configuration
@Profile({"dev", "hml"})
public class SeedConfig {

    private static final Logger log = LoggerFactory.getLogger(SeedConfig.class);

    // Permissões do ADMIN: gestão de usuários, leitura de roles/permissões, auditoria de negócio.
    // ADMIN NÃO pode criar/deletar roles ou permissões — isso é exclusivo do DEV.
    private static final String[] ADMIN_PERMISSIONS = {
        "USER_CREATE", "USER_READ", "USER_UPDATE", "USER_DELETE", "USER_ROLE_ASSIGN", "USER_STATUS",
        "ROLE_READ", "ROLE_MANAGE_PERMISSIONS",
        "PERMISSION_READ",
        "AUDIT_READ",
        "ESTOQUE_PRODUCT_READ", "ESTOQUE_PRODUCT_MANAGE", "ESTOQUE_PRODUCT_PRICE_MANAGE",
        "ESTOQUE_WAREHOUSE_READ", "ESTOQUE_WAREHOUSE_MANAGE",
        "ESTOQUE_STOCK_MANAGE",
        "ESTOQUE_RESERVATION_READ",
        "ESTOQUE_KIT_MANAGE",
        "ESTOQUE_CATEGORY_MANAGE",
        "ESTOQUE_BRAND_MANAGE",
        "ESTOQUE_REPLENISHMENT_MANAGE",
        "ESTOQUE_ATTRIBUTE_MANAGE",
        "CRM_CUSTOMER_READ", "CRM_CUSTOMER_MANAGE", "CRM_CUSTOMER_LOOKUP", "CRM_CUSTOMER_EXPORT",
        // CRM-C006 — cadastro rápido de lead (V127); o admin herda, o atendente recebe abaixo.
        "CRM_LEAD_CREATE",
        "CASHBACK_RATE_MANAGE", "CASHBACK_READ",
        "COMPRAS_READ", "COMPRAS_RECEIPT_MANAGE", "COMPRAS_SUPPLIER_MANAGE", "ECOMMERCE_READ",
        "FINANCEIRO_READ", "FINANCEIRO_CASH_FLOW_MANAGE", "LOGISTICA_READ",
        "PDV_READ", "PDV_SALE_MANAGE", "PDV_SALE_DISCOUNT",
        "PDV_SESSION_MANAGE", "PDV_SESSION_CLOSE", "PDV_COMANDA_MANAGE", "PDV_COMANDA_COURTESY",
        // PDV-C037 — fechar o caixa de outro operador (V145): era ROLE_ADMIN/ROLE_DEV fixo no código.
        "PDV_SESSION_CLOSE_ANY",
        // PDV-F043 — revisar venda offline recusada: reenviar ou descartar (V150).
        "PDV_OFFLINE_REVIEW",
        // PLAT-C059 — marcar (V137) e corrigir forma de pagamento (V136): sem elas o admin de dev,
        // com o Flyway desligado, tomava 403 na venda a prazo, na correção e nos recebíveis.
        "PDV_SALE_ON_ACCOUNT", "ORDER_PAYMENT_CORRECT", "ORDER_PAYMENT_CORRECT_CLOSED",
        "RECEIVABLE_READ", "RECEIVABLE_MANAGE",
        // PDV-F034 — sessão que vai ao preparo antes de paga: risco de calote, decisão da casa (V139).
        "PDV_SESSION_PAY_LATER",
        // PDV-F011 — como a COURTESY, só no admin: acréscimo manual é decisão da casa (V117).
        "PDV_COMANDA_SURCHARGE",
        // PDV-F021 — cadastro do cardápio de sessão (faixas, utensílios, duplo rosh), V128.
        "PDV_SESSAO_MANAGE",
        // PDV-F014 — mesma família: abater da conta no fechamento da mesa é alçada, não operação
        // de turno (V119). Separada de PDV_SALE_DISCOUNT, que é a alçada do balcão.
        "PDV_COMANDA_DISCOUNT",
        "ORDER_READ", "ORDER_FULFILL", "ORDER_CANCEL", "ORDER_REFUND",
        // Perfil da loja impresso no cupom (V131).
        "STORE_PROFILE_MANAGE",
        // Tokens de integração da loja — e-mail/Resend (V140), WhatsApp e n8n/Make (V143).
        "INTEGRATION_MANAGE",
        // Automações (Configurações › Automações): destino, segredo e disparo (V143).
        "AUTOMATION_MANAGE"
    };

    // Permissões do cliente do marketplace (Fatia 8/9) — NÃO entram em ADMIN_PERMISSIONS,
    // ROLE_CUSTOMER é uma role separada com escopo mínimo (plano-pdv-marketplace.md §2.9).
    private static final String[] SHOP_CUSTOMER_PERMISSIONS = {
        "SHOP_CART_OWN", "SHOP_ORDER_OWN", "SHOP_CASHBACK_OWN"
    };

    // Permissões do ROLE_ATENDENTE — espelham a V86 (o seed da role) mais PDV_COMANDA_MANAGE, que a
    // V105 concedeu só ao ROLE_ADMIN e a V111 estendeu ao atendente. Em dev o Flyway está desligado,
    // então sem esta lista a role nasce sem permissão nenhuma e o atendente toma 403 em todo o PDV;
    // as duas fontes precisam andar juntas — mexeu aqui, mexe na migration (e vice-versa).
    private static final String[] ATENDENTE_PERMISSIONS = {
        "PDV_READ", "PDV_SALE_MANAGE", "PDV_SALE_DISCOUNT",
        "PDV_SESSION_MANAGE", "PDV_SESSION_CLOSE", "PDV_COMANDA_MANAGE",
        "ESTOQUE_PRODUCT_READ", "ESTOQUE_WAREHOUSE_READ", "ESTOQUE_RESERVATION_READ",
        "CRM_CUSTOMER_READ", "CRM_CUSTOMER_LOOKUP", "CRM_LEAD_CREATE",
        "CASHBACK_READ",
        "ORDER_READ",
        // PLAT-C059 — a V137 concede a leitura de recebíveis também ao atendente.
        "RECEIVABLE_READ"
    };

    // DEV_ONLY_PERMISSIONS e ROLE_DEV são gerenciados pelo DevRoleBootstrapConfig (todos os profiles).

    @Bean
    CommandLineRunner seedAll(UserUseCase userUseCase,
                              RoleUseCase roleUseCase,
                              PermissionUseCase permissionUseCase,
                              CashbackUseCase cashbackUseCase,
                              @Value("${seed.admin.username:administrador}") String adminUsername,
                              @Value("${seed.admin.password:Administrador@2026!}") String adminPassword,
                              @Value("${seed.user.password:User@dev1}") String userPassword,
                              @Value("${seed.atendente.password:Atendente@dev1}") String atendentePassword) {
        return args -> {
            seedPermissions(permissionUseCase);
            seedRoles(roleUseCase);
            seedUsers(userUseCase, adminUsername, adminPassword, userPassword, atendentePassword);
            seedCashbackRate(cashbackUseCase);
        };
    }

    // Espelha o seed da V70 (taxa GLOBAL 3%) para o profile dev: aqui o schema nasce do ddl-auto a
    // partir das entities (spring.flyway.enabled=false), então a migration nunca roda e a taxa
    // GLOBAL da V70 jamais existiria sem isto — CashbackFlowIT (e o balcão em dev) ficariam sem
    // taxa aplicável.
    private void seedCashbackRate(CashbackUseCase cashbackUseCase) {
        try { cashbackUseCase.createRate(CashbackScope.GLOBAL, null, new BigDecimal("3.00"), null, null); }
        catch (Exception e) { log.debug("seed.cashbackRate.skip reason={}", e.getMessage()); }
    }

    private void seedPermissions(PermissionUseCase permissionUseCase) {
        for (String name : ADMIN_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("seed.permission.skip name={} reason={}", name, e.getMessage()); }
        }
        for (String name : SHOP_CUSTOMER_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("seed.permission.skip name={} reason={}", name, e.getMessage()); }
        }
        // Hoje toda ATENDENTE_PERMISSIONS já está em ADMIN_PERMISSIONS; o loop existe para que uma
        // permissão exclusiva do atendente não nasça inexistente (assignPermission falharia calado).
        Set<String> criadas = new HashSet<>(List.of(ADMIN_PERMISSIONS));
        for (String name : ATENDENTE_PERMISSIONS) {
            if (!criadas.add(name)) continue;
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("seed.permission.skip name={} reason={}", name, e.getMessage()); }
        }
        // DEV_ONLY_PERMISSIONS e ROLE_DEV são gerenciados pelo DevRoleBootstrapConfig (todos os profiles).
    }

    private void seedRoles(RoleUseCase roleUseCase) {
        for (String name : new String[]{"ROLE_ADMIN", "ROLE_USER", "ROLE_CUSTOMER", "ROLE_ATENDENTE"}) {
            try { roleUseCase.createRole(name); }
            catch (Exception e) { log.debug("seed.role.skip name={} reason={}", name, e.getMessage()); }
        }

        for (String perm : ADMIN_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_ADMIN", perm); }
            catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_ADMIN perm={} reason={}", perm, e.getMessage()); }
        }

        try { roleUseCase.assignPermission("ROLE_USER", "USER_READ"); }
        catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_USER perm=USER_READ reason={}", e.getMessage()); }

        for (String perm : SHOP_CUSTOMER_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_CUSTOMER", perm); }
            catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_CUSTOMER perm={} reason={}", perm, e.getMessage()); }
        }

        for (String perm : ATENDENTE_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_ATENDENTE", perm); }
            catch (Exception e) { log.debug("seed.role.assignPermission.skip role=ROLE_ATENDENTE perm={} reason={}", perm, e.getMessage()); }
        }
    }

    private void seedUsers(UserUseCase userUseCase, String adminUsername, String adminPassword, String userPassword, String atendentePassword) {
        if (userUseCase.findByUsername(adminUsername).isEmpty()) {
            try { userUseCase.createUser(adminUsername, adminPassword, "administrador@cernedsgn.xyz", List.of("ROLE_ADMIN")); }
            catch (Exception e) { log.debug("seed.user.skip name={} reason={}", adminUsername, e.getMessage()); }
        }
        if (userUseCase.findByUsername("admin").isEmpty()) {
            try { userUseCase.createUser("admin", adminPassword, "admin@cernedsgn.xyz", List.of("ROLE_ADMIN")); }
            catch (Exception e) { log.debug("seed.user.skip name=admin reason={}", e.getMessage()); }
        }
        if (userUseCase.findByUsername("user").isEmpty()) {
            try { userUseCase.createUser("user", userPassword, List.of("ROLE_USER")); }
            catch (Exception e) { log.debug("seed.user.skip name=user reason={}", e.getMessage()); }
        }
        if (userUseCase.findByUsername("atendente").isEmpty()) {
            try { userUseCase.createUser("atendente", atendentePassword, List.of("ROLE_ATENDENTE")); }
            catch (Exception e) { log.debug("seed.user.skip name=atendente reason={}", e.getMessage()); }
        }
    }
}
