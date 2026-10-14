package com.cernecommerce.core.domain.exception.estoque;

/**
 * Já há um depósito do Salão ativo. Precisa ser único porque é ele que a sessão de narguilé
 * consome, seja qual for o caixa da comanda: com dois, a lata de um sabor teria dois lugares
 * possíveis e o PDV não teria como escolher.
 */
public class SalaoWarehouseAlreadyExistsException extends RuntimeException {
    public SalaoWarehouseAlreadyExistsException(String existingCode) {
        super("Já existe um depósito do Salão ativo: " + existingCode);
    }
}
