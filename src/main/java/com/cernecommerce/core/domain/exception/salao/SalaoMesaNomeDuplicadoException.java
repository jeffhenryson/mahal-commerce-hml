package com.cernecommerce.core.domain.exception.salao;

/** Já existe mesa de autoatendimento com este nome (sem diferenciar maiúsculas). */
public class SalaoMesaNomeDuplicadoException extends RuntimeException {
    public SalaoMesaNomeDuplicadoException(String message) {
        super(message);
    }
}
