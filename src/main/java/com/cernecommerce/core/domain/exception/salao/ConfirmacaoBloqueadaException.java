package com.cernecommerce.core.domain.exception.salao;

/**
 * O cliente errou a confirmação vezes demais em pouco tempo: até a janela passar, nem a certa entra.
 * Quem está na mesa ainda passa o código de 4 dígitos.
 */
public class ConfirmacaoBloqueadaException extends RuntimeException {

    private final long retryAfterSeconds;

    public ConfirmacaoBloqueadaException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
