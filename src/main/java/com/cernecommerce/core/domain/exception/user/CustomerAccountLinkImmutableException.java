package com.cernecommerce.core.domain.exception.user;

/**
 * PLAT-F005 — a conta de cliente da loja ({@code user_type = CUSTOMER}) nasce ligada ao seu cadastro
 * do CRM e não troca de cliente: desvincular ou religar quebraria o login do comprador. O vínculo
 * administrável é só o da conta de operador.
 */
public class CustomerAccountLinkImmutableException extends RuntimeException {
    public CustomerAccountLinkImmutableException(String username) {
        super("O vínculo de uma conta de cliente da loja não pode ser alterado: " + username);
    }
}
