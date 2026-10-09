package com.cernecommerce.core.domain.exception.estoque;

/**
 * Ligação de embalagem recusada (EST-F032): kit, produto base com variações, produto com lote,
 * ciclo ou cadeia funda demais. 400 — o que está errado é o pedido de cadastro, e a mensagem diz
 * qual das regras ele violou.
 */
public class InvalidPackagingException extends RuntimeException {
    public InvalidPackagingException(String message) {
        super(message);
    }
}
