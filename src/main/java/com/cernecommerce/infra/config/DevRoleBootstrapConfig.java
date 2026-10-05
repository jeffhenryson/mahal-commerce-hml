package com.cernecommerce.infra.config;

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

import java.util.List;

/**
 * Garante que ROLE_DEV e suas permissões exclusivas existam em todos os ambientes.
 * Roda em dev, hml e prod — o usuário DEV é criado apenas quando DEV_EMAIL está definido.
 *
 * Separado do SeedConfig (dev-only) que cria usuários de teste (admin/user).
 */
@Configuration
@Profile({"dev", "hml", "prod"})
public class DevRoleBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(DevRoleBootstrapConfig.class);

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
        // Tokens de integração da loja — e-mail/Resend (V140).
        "INTEGRATION_MANAGE"
    };

    private static final String[] DEV_ONLY_PERMISSIONS = {
        "DEV_ROLE_MANAGE",
        "DEV_PERMISSION_MANAGE"
    };

    @Bean
    CommandLineRunner bootstrapDevRole(PermissionUseCase permissionUseCase,
                                       RoleUseCase roleUseCase,
                                       UserUseCase userUseCase,
                                       @Value("${seed.dev.email:}") String devEmail,
                                       @Value("${seed.dev.password:Dev@secure1!}") String devPassword) {
        return args -> {
            ensureDevPermissions(permissionUseCase);
            ensureDevRole(roleUseCase);
            ensureDevUser(userUseCase, devEmail, devPassword);
        };
    }

    private void ensureDevPermissions(PermissionUseCase permissionUseCase) {
        for (String name : ADMIN_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("dev-bootstrap.permission.skip name={}", name); }
        }
        for (String name : DEV_ONLY_PERMISSIONS) {
            try { permissionUseCase.createPermission(name); }
            catch (Exception e) { log.debug("dev-bootstrap.permission.skip name={}", name); }
        }
    }

    private void ensureDevRole(RoleUseCase roleUseCase) {
        try { roleUseCase.createRole("ROLE_DEV"); }
        catch (Exception e) { log.debug("dev-bootstrap.role.skip ROLE_DEV (já existe)"); }

        for (String perm : ADMIN_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_DEV", perm); }
            catch (Exception e) { log.debug("dev-bootstrap.role.assign.skip perm={}", perm); }
        }
        for (String perm : DEV_ONLY_PERMISSIONS) {
            try { roleUseCase.assignPermission("ROLE_DEV", perm); }
            catch (Exception e) { log.debug("dev-bootstrap.role.assign.skip perm={}", perm); }
        }
    }

    private void ensureDevUser(UserUseCase userUseCase, String devEmail, String devPassword) {
        if (devEmail.isBlank()) return;
        String devUsername = devEmail.contains("@") ? devEmail.split("@")[0] : devEmail;
        if (userUseCase.findByUsername(devUsername).isEmpty()) {
            userUseCase.createUser(devUsername, devPassword, devEmail, List.of("ROLE_DEV"));
            log.info("dev-bootstrap.user.created username={} env={}", devUsername,
                    System.getProperty("spring.profiles.active", "unknown"));
        }
    }
}
