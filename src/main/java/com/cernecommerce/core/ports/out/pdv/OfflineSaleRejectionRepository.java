package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;

import java.util.List;
import java.util.Optional;

/** Vendas offline recusadas esperando revisão (PDV-F043). */
public interface OfflineSaleRejectionRepository {

    OfflineSaleRejection save(OfflineSaleRejection rejection);

    Optional<OfflineSaleRejection> findById(Long id);

    /** A recusa de uma venda já sincronizada antes — reenviar a mesma venda não cria outra. */
    Optional<OfflineSaleRejection> findByClientSaleId(String clientSaleId);

    /** Todas as do caixa, pendentes e resolvidas, mais recentes primeiro. */
    List<OfflineSaleRejection> findBySessionId(Long sessionId);

    /** Ids das pendentes do caixa — o que barra o fechamento. */
    List<Long> findPendingIdsBySessionId(Long sessionId);
}
