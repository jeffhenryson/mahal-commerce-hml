package com.cernecommerce.core.domain.exception.crm;

/**
 * V206 — o perfil do cliente não pode ser excluído com saldo devedor em Marcados (fiado em aberto):
 * a dívida perderia a quem cobrar. → 409 CUSTOMER_HAS_OPEN_BALANCE.
 */
public class CustomerHasOpenBalanceException extends RuntimeException {
    public CustomerHasOpenBalanceException(Long customerId) {
        super("O cliente " + customerId + " tem saldo devedor em Marcados. Quite ou cancele antes de excluir o perfil.");
    }
}
