package com.cernecommerce.core.domain.exception.estoque;

/**
 * Rascunho não se arquiva (EST-F054) — o espelho de {@link ProductNotDraftException}.
 *
 * <p>Rascunho nunca foi publicado e não tem histórico a preservar, então a exclusão dele é a de
 * verdade, por {@code DELETE /estoque/products/{sku}}. Arquivar um rascunho só deixaria um
 * cadastro incompleto ocupando o SKU para sempre.</p>
 */
public class ProductIsDraftException extends RuntimeException {
    public ProductIsDraftException(String sku) {
        super("Produto " + sku + " é um rascunho: exclua com DELETE /estoque/products/" + sku);
    }
}
