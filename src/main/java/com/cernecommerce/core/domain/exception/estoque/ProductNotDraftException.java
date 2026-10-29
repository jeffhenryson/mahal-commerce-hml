package com.cernecommerce.core.domain.exception.estoque;

/**
 * Só rascunho pode ser excluído de fato (EST-F026).
 *
 * <p>A restrição é o que separa "descartar um cadastro que nunca foi publicado" de "apagar um
 * produto do catálogo". O segundo não existe e não deve existir: SKU é referenciado como texto
 * livre por {@code stock_balance}, {@code stock_movement}, {@code order_item} e
 * {@code comanda_item}, sem FK (EST-C011), e apagar o produto deixaria esse histórico órfão. Para
 * retirar de circulação um produto publicado, o caminho continua sendo
 * {@code PATCH /estoque/products/{sku}/active} com {@code active: false}, que preserva tudo.</p>
 */
public class ProductNotDraftException extends RuntimeException {
    public ProductNotDraftException(String sku) {
        super("Produto " + sku + " não é um rascunho e não pode ser excluído: "
                + "use POST /estoque/products/" + sku + "/archive para excluí-lo preservando o histórico");
    }
}
