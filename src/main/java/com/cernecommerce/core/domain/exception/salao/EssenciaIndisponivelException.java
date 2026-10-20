package com.cernecommerce.core.domain.exception.salao;

/** A essência pedida não está entre as oferecidas agora (acabou, saiu das do dia ou sem faixa). */
public class EssenciaIndisponivelException extends RuntimeException {
    public EssenciaIndisponivelException(String message) {
        super(message);
    }
}
