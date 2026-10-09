package com.cernecommerce.core.domain.exception.pdv;

import java.util.List;

/**
 * O caixa tem vendas offline recusadas esperando revisão (PDV-F043) e não fecha até elas serem
 * reenviadas ou descartadas — mesma natureza do bloqueio por mesa aberta (PDV-C005).
 */
public class SessionHasPendingOfflineSalesException extends RuntimeException {

    private final List<Long> rejectionIds;

    public SessionHasPendingOfflineSalesException(Long sessionId, List<Long> rejectionIds) {
        super("O caixa #" + sessionId + " tem " + rejectionIds.size()
                + " venda(s) offline recusada(s) esperando revisão: " + rejectionIds);
        this.rejectionIds = List.copyOf(rejectionIds);
    }

    public List<Long> getRejectionIds() {
        return rejectionIds;
    }
}
