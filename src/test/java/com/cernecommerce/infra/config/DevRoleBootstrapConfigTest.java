package com.cernecommerce.infra.config;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garante que ROLE_DEV receba as permissões dos domínios de negócio protegidos por
 * {@code @PreAuthorize}. O caso do {@code PDV_SALE_MANAGE} (EST-C001) é o que quebrou:
 * a permissão existia só no controller e na migration V57 (concedida a ROLE_ADMIN),
 * então o DEV tomava 403 em {@code POST /pdv/sessions/{id}/sales}.
 */
class DevRoleBootstrapConfigTest {

    private final DevRoleBootstrapConfig config = new DevRoleBootstrapConfig();

    private CommandLineRunner runnerWith(PermissionUseCase permissionUseCase, RoleUseCase roleUseCase) {
        return config.bootstrapDevRole(permissionUseCase, roleUseCase, mock(UserUseCase.class), "", "Dev@secure1!");
    }

    @Test
    void bootstrapDevRole_grantsPdvSaleManageToRoleDev() throws Exception {
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);

        runnerWith(permissionUseCase, roleUseCase).run();

        verify(permissionUseCase).createPermission("PDV_SALE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "PDV_SALE_MANAGE");
    }

    @Test
    void bootstrapDevRole_grantsEstoqueAndPdvReadPermissionsToRoleDev() throws Exception {
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);

        runnerWith(permissionUseCase, roleUseCase).run();

        verify(roleUseCase).assignPermission("ROLE_DEV", "ESTOQUE_STOCK_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "ESTOQUE_PRODUCT_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "ESTOQUE_PRODUCT_PRICE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "ESTOQUE_WAREHOUSE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "PDV_READ");
    }

    /**
     * PDV-F009: sem esta no seed, comanda de mesa responde 403 em dev, mesmo sintoma que
     * PDV_SALE_MANAGE já causou em EST-C001.
     */
    @Test
    void bootstrapDevRole_grantsPdvComandaManageToRoleDev() throws Exception {
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);

        runnerWith(permissionUseCase, roleUseCase).run();

        verify(permissionUseCase).createPermission("PDV_COMANDA_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "PDV_COMANDA_MANAGE");
    }

    @Test
    void bootstrapDevRole_grantsDevOnlyPermissionsToRoleDev() throws Exception {
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);

        runnerWith(permissionUseCase, roleUseCase).run();

        verify(roleUseCase).assignPermission("ROLE_DEV", "DEV_ROLE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_DEV", "DEV_PERMISSION_MANAGE");
    }

    @Test
    void bootstrapDevRole_doesNotCreateDevUserWhenEmailIsBlank() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);

        config.bootstrapDevRole(mock(PermissionUseCase.class), mock(RoleUseCase.class), userUseCase,
                "", "Dev@secure1!").run();

        verify(userUseCase, org.mockito.Mockito.never())
                .createUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyList());
    }

    /**
     * Marcados e limite de crédito: RECEIVABLE_* nasceram só no ROLE_ADMIN (V137). O DEV recebe
     * toda permissão gravada no banco, menos as de cliente do marketplace.
     */
    @Test
    void bootstrapDevRole_grantsEveryExistingPermissionExceptShopToRoleDev() throws Exception {
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        when(permissionUseCase.listAll(eq(0), anyInt())).thenReturn(new PageResult<>(
                List.of(new Permission("RECEIVABLE_READ"), new Permission("RECEIVABLE_MANAGE"),
                        new Permission("SHOP_CART_OWN")), 0, 100, 3, 1));

        runnerWith(permissionUseCase, roleUseCase).run();

        verify(roleUseCase).assignPermission("ROLE_DEV", "RECEIVABLE_READ");
        verify(roleUseCase).assignPermission("ROLE_DEV", "RECEIVABLE_MANAGE");
        verify(roleUseCase, never()).assignPermission("ROLE_DEV", "SHOP_CART_OWN");
    }

    @Test
    void bootstrapDevRole_walksEveryPermissionPage() throws Exception {
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        when(permissionUseCase.listAll(eq(0), anyInt())).thenReturn(new PageResult<>(
                List.of(new Permission("ORDER_PAYMENT_CORRECT")), 0, 1, 2, 2));
        when(permissionUseCase.listAll(eq(1), anyInt())).thenReturn(new PageResult<>(
                List.of(new Permission("ORDER_PAYMENT_CORRECT_CLOSED")), 1, 1, 2, 2));

        runnerWith(permissionUseCase, roleUseCase).run();

        verify(roleUseCase).assignPermission("ROLE_DEV", "ORDER_PAYMENT_CORRECT");
        verify(roleUseCase).assignPermission("ROLE_DEV", "ORDER_PAYMENT_CORRECT_CLOSED");
    }
}
