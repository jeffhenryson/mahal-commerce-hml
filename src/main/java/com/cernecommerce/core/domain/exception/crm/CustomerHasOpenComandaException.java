package com.cernecommerce.core.domain.exception.crm;

/**
 * V206 — o perfil do cliente não pode ser excluído enquanto houver comanda, mesa ou solicitação de
 * sessão aberta vinculada a ele: o atendimento em curso ficaria sem dono. → 409 CUSTOMER_HAS_OPEN_COMANDA.
 */
public class CustomerHasOpenComandaException extends RuntimeException {
    public CustomerHasOpenComandaException(Long customerId) {
        super("O cliente " + customerId + " tem comanda, mesa ou sessão aberta. Encerre-a antes de excluir o perfil.");
    }
}
