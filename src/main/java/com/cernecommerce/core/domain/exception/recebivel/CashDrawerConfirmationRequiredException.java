package com.cernecommerce.core.domain.exception.recebivel;

/**
 * Quitação de marcado com linha DINHEIRO sem {@code cashConfirmed=true}: o operador não confirmou que
 * o valor está na gaveta. Caixa 62 — o DINHEIRO pré-selecionado do diálogo foi gravado sem o dinheiro
 * ter entrado, e o fechamento acusou falta.
 */
public class CashDrawerConfirmationRequiredException extends RuntimeException {

    public CashDrawerConfirmationRequiredException() {
        super("Confirme que o valor em dinheiro está na gaveta (cashConfirmed=true)");
    }
}
