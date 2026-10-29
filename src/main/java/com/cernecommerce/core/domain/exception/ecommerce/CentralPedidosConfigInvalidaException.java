package com.cernecommerce.core.domain.exception.ecommerce;

/** Configuração do recebimento com campo fora da regra (ECM-F013); a mensagem junta todos os problemas. */
public class CentralPedidosConfigInvalidaException extends RuntimeException {

    public CentralPedidosConfigInvalidaException(String message) {
        super(message);
    }
}
