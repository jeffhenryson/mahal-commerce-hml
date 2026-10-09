package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;

/** Item de uma venda feita offline no balcão, guardado como chegou (PDV-F043). */
public record OfflineSaleItem(String sku, BigDecimal quantity, BigDecimal discountAmount, String note) {
}
