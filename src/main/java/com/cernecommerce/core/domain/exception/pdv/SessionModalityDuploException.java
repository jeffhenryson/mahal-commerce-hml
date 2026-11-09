package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — o duplo (2º rosh) só existe na sessão padrão. Compra de essência e itens pessoais recusam
 * o duplo no lançamento e no "Tornar duplo".
 * 409 SESSION_MODALITY_DUPLO.
 */
public class SessionModalityDuploException extends RuntimeException {

    public SessionModalityDuploException(String modalidade) {
        super("O duplo só vale na sessão padrão (modalidade " + modalidade + ")");
    }
}
