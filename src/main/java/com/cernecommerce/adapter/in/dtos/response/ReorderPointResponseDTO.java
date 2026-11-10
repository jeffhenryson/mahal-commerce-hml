package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Ponto de reposição (quantidade mínima) de um SKU em um depósito.
 *
 * <p>{@code minQuantity} nulo significa que este SKU/depósito não tem ponto de reposição
 * configurado — diferente de {@code 0}, que é um limiar válido escolhido explicitamente.</p>
 */
@Data
public class ReorderPointResponseDTO {
    private String sku;
    private String warehouseCode;
    private BigDecimal minQuantity;
    /** Crítico configurado, cru: nulo = padrão (metade do mínimo), calculado pelo cliente. */
    private BigDecimal criticalQuantity;
}
