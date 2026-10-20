package com.cernecommerce.core.domain.exception.salao;

/**
 * CPF, celular ou nome inválido na página pública do QR. A mensagem vai para o lead como está — o
 * {@code IllegalArgumentException} genérico vira "Requisição inválida", que não diz o que corrigir.
 */
public class AutoatendimentoDadosInvalidosException extends RuntimeException {
    public AutoatendimentoDadosInvalidosException(String message) {
        super(message);
    }
}
