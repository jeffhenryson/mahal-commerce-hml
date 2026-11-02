package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;

/**
 * Item de uma venda feita offline no balcão, guardado como chegou (PDV-F043).
 *
 * <p>{@code kitQuantity} e {@code promotionDiscountAmount} são da promoção por quantidade: as unidades
 * presas a kit montável e o desconto que o caixa aplicou offline. A venda já foi cobrada, então na
 * sincronização vale o valor do caixa se o servidor divergir. Nulos em payload gravado antes deles.</p>
 */
public record OfflineSaleItem(String sku, BigDecimal quantity, BigDecimal discountAmount, String note,
        Integer kitQuantity, BigDecimal promotionDiscountAmount) {

    public OfflineSaleItem(String sku, BigDecimal quantity, BigDecimal discountAmount, String note) {
        this(sku, quantity, discountAmount, note, null, null);
    }
}
