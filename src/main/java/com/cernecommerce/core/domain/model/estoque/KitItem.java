package com.cernecommerce.core.domain.model.estoque;

import java.math.BigDecimal;

/**
 * Item de um kit pré-montado ({@link KitMode#PRE_MONTADO}). Só existe dentro do kit: não é produto,
 * não tem SKU nem saldo e não aparece no catálogo, no PDV como item vendável, no marketplace nem em
 * outro kit. Serve para descrever o conteúdo e somar o custo do kit.
 *
 * <p>{@code categoryName}/{@code brandName} são de leitura — resolvidos pelo service a partir dos
 * ids, não persistidos.</p>
 */
public record KitItem(Long id, Long kitProductId, String name, int quantity, BigDecimal unitCost,
                      Long categoryId, String categoryName, Long brandId, String brandName,
                      String description, String imageUrl, int position) {

    public KitItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome do item do kit é obrigatório");
        }
        name = name.strip();
        if (quantity < 1) {
            throw new IllegalArgumentException("quantidade do item do kit deve ser maior ou igual a 1");
        }
        if (unitCost != null && unitCost.signum() < 0) {
            throw new IllegalArgumentException("custo unitário do item do kit não pode ser negativo");
        }
    }

    /** Custo da linha ({@code unitCost × quantity}); nulo quando o item não tem custo. */
    public BigDecimal lineCost() {
        return unitCost == null ? null : unitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public KitItem withNames(String newCategoryName, String newBrandName) {
        return new KitItem(id, kitProductId, name, quantity, unitCost, categoryId, newCategoryName, brandId,
                newBrandName, description, imageUrl, position);
    }
}
