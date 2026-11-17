package com.cernecommerce.core.domain.exception.estoque;

/**
 * Variante não pode ser excluída enquanto houver saldo ou movimentação de estoque gravados para
 * o seu SKU — {@code stock_balance}/{@code stock_movement} referenciam SKU como texto livre, sem
 * FK, então apagar a variante deixaria esse histórico órfão. Use
 * {@code PATCH .../variants/{variantSku} {"active": false}} para retirar a variante de circulação
 * preservando o histórico.
 */
public class VariantHasStockHistoryException extends RuntimeException {
    public VariantHasStockHistoryException(String productSku, String variantSku) {
        super("Variante " + variantSku + " do produto " + productSku
                + " não pode ser removida: há saldo ou movimentação de estoque gravados para este SKU");
    }

    private VariantHasStockHistoryException(String message) {
        super(message);
    }

    /**
     * A variante tem lata de essência registrada ({@code open_package}), aberta ou fechada. Caso à
     * parte do saldo/movimento: a lata cadastrada como "já aberta", sem baixa, não deixa nenhum dos
     * dois, e apagar a variante deixava a lata apontando para um SKU sem produto.
     */
    public static VariantHasStockHistoryException comLataRegistrada(String productSku, String variantSku) {
        return new VariantHasStockHistoryException("Variante " + variantSku + " do produto " + productSku
                + " não pode ser removida: há lata de essência registrada para este SKU. "
                + "Inative a variação em vez de excluir");
    }
}
