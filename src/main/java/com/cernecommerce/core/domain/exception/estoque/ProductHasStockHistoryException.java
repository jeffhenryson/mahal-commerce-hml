package com.cernecommerce.core.domain.exception.estoque;

/**
 * Rascunho com saldo ou movimentação gravados não pode ser excluído (EST-F026).
 *
 * <p>Mesma régua de {@link VariantHasStockHistoryException}: {@code stock_balance} e
 * {@code stock_movement} referenciam SKU como texto livre, sem FK (EST-C011), então apagar o
 * produto deixaria histórico órfão — exatamente o passivo que aquele card levantou. Rascunho com
 * movimentação é raro mas possível: a criação atômica com estoque inicial já grava
 * {@code ENTRADA}.</p>
 */
public class ProductHasStockHistoryException extends RuntimeException {
    public ProductHasStockHistoryException(String sku) {
        super("Rascunho " + sku + " não pode ser excluído: há saldo ou movimentação de estoque "
                + "gravados para este SKU");
    }

    private ProductHasStockHistoryException(String message) {
        super(message);
    }

    /** O SKU (pai ou variação) tem lata de essência registrada, aberta ou fechada. */
    public static ProductHasStockHistoryException comLataRegistrada(String sku) {
        return new ProductHasStockHistoryException("Rascunho não pode ser excluído: há lata de essência "
                + "registrada para o SKU " + sku + ". Inative o produto ou a variação em vez de excluir");
    }
}
