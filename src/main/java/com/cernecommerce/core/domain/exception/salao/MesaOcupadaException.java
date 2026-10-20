package com.cernecommerce.core.domain.exception.salao;

/** A mesa está com outro cliente: este aparelho precisa do código de 4 dígitos para pedir ou ver. */
public class MesaOcupadaException extends RuntimeException {
    public MesaOcupadaException(String message) {
        super(message);
    }
}
