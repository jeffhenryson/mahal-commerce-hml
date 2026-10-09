package com.cernecommerce.infra.config;

import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.PermissionUseCase;
import com.cernecommerce.core.ports.in.RoleUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Garante que o usuário {@code admin} seedado em dev receba as mesmas permissões de estoque
 * que {@link DevRoleBootstrapConfig} concede a ROLE_DEV — sem isso, o admin de teste local
 * recebe 403 inesperado em {@code /estoque/**} (C006).
 */
class SeedConfigTest {

    private final SeedConfig seedConfig = new SeedConfig();

    @Test
    void seedAll_grantsEstoquePermissionsToRoleAdmin() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "administrador", "Admin@dev1", "User@dev1", "Atendente@dev1");
        runner.run();

        verify(permissionUseCase).createPermission("ESTOQUE_PRODUCT_READ");
        verify(permissionUseCase).createPermission("ESTOQUE_PRODUCT_MANAGE");
        verify(permissionUseCase).createPermission("ESTOQUE_WAREHOUSE_READ");
        verify(permissionUseCase).createPermission("ESTOQUE_WAREHOUSE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_PRODUCT_READ");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_PRODUCT_MANAGE");
        // EST-F019 — sem esta no seed, precificar responde 403 em dev, que é exatamente o
        // sintoma que EST-C001 já custou uma sessão de depuração no PDV_SALE_MANAGE.
        verify(permissionUseCase).createPermission("ESTOQUE_PRODUCT_PRICE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_PRODUCT_PRICE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_WAREHOUSE_READ");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "ESTOQUE_WAREHOUSE_MANAGE");
    }

    /**
     * {@code PDV_SALE_MANAGE} protege {@code POST /pdv/sessions/{id}/sales}, que é o caminho de
     * baixa automática de estoque. Sem ele no seed, o admin de dev toma 403 ao registrar venda
     * e o fluxo PDV → estoque fica inalcançável localmente (EST-C001).
     */
    @Test
    void seedAll_grantsPdvSaleManageToRoleAdmin() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "administrador", "Admin@dev1", "User@dev1", "Atendente@dev1");
        runner.run();

        verify(permissionUseCase).createPermission("PDV_SALE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "PDV_SALE_MANAGE");
    }

    /** PDV-F009: mesmo sintoma de EST-C001 — sem esta no seed, comanda de mesa responde 403 em dev. */
    @Test
    void seedAll_grantsPdvComandaManageToRoleAdmin() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "administrador", "Admin@dev1", "User@dev1", "Atendente@dev1");
        runner.run();

        verify(permissionUseCase).createPermission("PDV_COMANDA_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ADMIN", "PDV_COMANDA_MANAGE");
    }

    /**
     * A V105 concedeu {@code PDV_COMANDA_MANAGE} só ao ROLE_ADMIN, e o seed nunca concedeu nada ao
     * ROLE_ATENDENTE — que é quem opera comanda de mesa. Em {@code dev} o Flyway está desligado, então
     * a role nascia sem permissão alguma e o atendente tomava 403 em todo o PDV, não só na comanda.
     */
    @Test
    void seedAll_grantsPdvPermissionsToRoleAtendente() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "administrador", "Admin@dev1", "User@dev1", "Atendente@dev1");
        runner.run();

        verify(roleUseCase).createRole("ROLE_ATENDENTE");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "PDV_COMANDA_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "PDV_READ");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "PDV_SALE_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "PDV_SESSION_MANAGE");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "PDV_SESSION_CLOSE");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "ESTOQUE_PRODUCT_READ");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "CRM_CUSTOMER_LOOKUP");
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "ORDER_READ");
    }

    /**
     * PLAT-C059 + PDV-C037 — as permissões de V136/V137/V145 não estavam no seed: em dev, com o
     * Flyway desligado, o admin tomava 403 ao marcar, corrigir pagamento e ver recebíveis. Fechar
     * caixa alheio é do admin; o atendente só lê recebíveis.
     */
    @Test
    void seedAll_grantsOnAccountCorrectionReceivableAndCloseAnyPermissions() throws Exception {
        UserUseCase userUseCase = mock(UserUseCase.class);
        RoleUseCase roleUseCase = mock(RoleUseCase.class);
        PermissionUseCase permissionUseCase = mock(PermissionUseCase.class);
        CashbackUseCase cashbackUseCase = mock(CashbackUseCase.class);
        when(userUseCase.findByUsername(anyString())).thenReturn(Optional.empty());

        CommandLineRunner runner = seedConfig.seedAll(userUseCase, roleUseCase, permissionUseCase,
                cashbackUseCase, "administrador", "Admin@dev1", "User@dev1", "Atendente@dev1");
        runner.run();

        for (String perm : new String[] {"PDV_SESSION_CLOSE_ANY", "PDV_SALE_ON_ACCOUNT", "ORDER_PAYMENT_CORRECT",
                "ORDER_PAYMENT_CORRECT_CLOSED", "RECEIVABLE_READ", "RECEIVABLE_MANAGE"}) {
            verify(permissionUseCase).createPermission(perm);
            verify(roleUseCase).assignPermission("ROLE_ADMIN", perm);
        }
        verify(roleUseCase).assignPermission("ROLE_ATENDENTE", "RECEIVABLE_READ");
        verify(roleUseCase, org.mockito.Mockito.never()).assignPermission("ROLE_ATENDENTE", "PDV_SESSION_CLOSE_ANY");
    }
}
