package com.cernecommerce.adapter.in.dtos.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * O que entrou num depósito por transferência no período, a preço de custo (EST-F039) — "quanto o
 * Salão consumiu de essência".
 */
public record TransferSummaryResponseDTO(List<Item> items, BigDecimal totalQuantity, BigDecimal totalCost) {

    public record Item(String sku, String productName, BigDecimal quantity, BigDecimal totalCost) {
    }
}
