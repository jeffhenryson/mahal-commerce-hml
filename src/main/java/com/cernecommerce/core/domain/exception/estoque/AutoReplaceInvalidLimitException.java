package com.cernecommerce.core.domain.exception.estoque;

/**
 * "Repor sozinha" — o limite da troca automática da lata é inválido: fora de 1..99, ou a função foi
 * ligada sem limite ({@code null} = usar o {@code sessionsPerUnit}) num produto que não declara
 * {@code sessionsPerUnit}, e aí não haveria número nenhum para comparar com os usos.
 */
public class AutoReplaceInvalidLimitException extends RuntimeException {
    public AutoReplaceInvalidLimitException(String message) {
        super(message);
    }
}
