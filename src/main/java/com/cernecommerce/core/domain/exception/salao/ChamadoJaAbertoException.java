package com.cernecommerce.core.domain.exception.salao;

/** A mesa já tem um chamado aberto do mesmo tipo — a equipe já foi avisada. */
public class ChamadoJaAbertoException extends RuntimeException {
    public ChamadoJaAbertoException(String message) {
        super(message);
    }
}
