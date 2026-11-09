package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — com a sessão já na mesa só entram os adicionais marcados "pode incluir após entrega".
 * 409 SESSION_ADDON_NOT_AFTER_DELIVERY.
 */
public class SessionAddonNotAfterDeliveryException extends RuntimeException {

    public SessionAddonNotAfterDeliveryException(String nome) {
        super("O adicional " + nome + " não pode ser incluído depois da entrega");
    }
}
