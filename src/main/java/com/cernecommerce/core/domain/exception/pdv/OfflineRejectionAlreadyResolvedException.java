package com.cernecommerce.core.domain.exception.pdv;

/** A venda offline recusada já saiu da revisão (PDV-F043): reenviar ou descartar de novo é 409. */
public class OfflineRejectionAlreadyResolvedException extends RuntimeException {
    public OfflineRejectionAlreadyResolvedException(Long id, String resolution) {
        super("A venda offline recusada #" + id + " já foi resolvida (" + resolution + ")");
    }
}
