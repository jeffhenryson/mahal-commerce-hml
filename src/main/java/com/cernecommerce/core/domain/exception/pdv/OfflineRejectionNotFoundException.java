package com.cernecommerce.core.domain.exception.pdv;

public class OfflineRejectionNotFoundException extends RuntimeException {
    public OfflineRejectionNotFoundException(Long id) {
        super("Venda offline recusada não encontrada: #" + id);
    }
}
