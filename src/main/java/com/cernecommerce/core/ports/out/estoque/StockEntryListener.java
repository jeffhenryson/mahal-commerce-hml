package com.cernecommerce.core.ports.out.estoque;

import java.util.Collection;

/**
 * Avisado depois do commit quando houve movimentação de ENTRADA nesses SKUs (produto e, se for variação, também
 * o SKU do pai). Usado pelo gatilho opcional dos pedidos de clientes
 * ({@code estoque.product-requests.auto-arrive-on-stock-entry}). A implementação roda fora da thread da
 * movimentação e em transação própria.
 */
public interface StockEntryListener {

    void onStockEntry(Collection<String> skus);
}
