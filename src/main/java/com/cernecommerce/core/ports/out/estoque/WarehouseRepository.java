package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;

import java.util.Optional;

/**
 * Port de saída para persistência de depósitos de estoque.
 */
public interface WarehouseRepository {

    Warehouse save(Warehouse warehouse);

    Optional<Warehouse> findByCode(String code);

    /**
     * Busca por id. Necessária porque {@code StockCount} e {@code StockBalance} guardam
     * {@code warehouseId}, enquanto a API trabalha com {@code code}.
     */
    Optional<Warehouse> findById(Long id);

    /** Depósitos paginados, ordenados por id. */
    PageResult<Warehouse> findAll(int page, int size);

    /**
     * O depósito ativo de um tipo. Existe para o {@link WarehouseType#SALAO}, que é único entre os
     * ativos (índice parcial da V157); para os outros tipos devolve o de menor id.
     */
    Optional<Warehouse> findActiveByType(WarehouseType type);
}
