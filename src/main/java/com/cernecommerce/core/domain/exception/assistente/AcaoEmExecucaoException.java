package com.cernecommerce.core.domain.exception.assistente;

/** Segundo confirmar enquanto o primeiro ainda executa (409 ACAO_EM_EXECUCAO). */
public class AcaoEmExecucaoException extends RuntimeException {
    public AcaoEmExecucaoException() {
        super("Esta ação já está sendo executada.");
    }
}
