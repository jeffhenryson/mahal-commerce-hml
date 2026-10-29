package com.cernecommerce.core.domain.exception.estoque;

/**
 * O SKU pedido pertence a um produto arquivado (EST-F054).
 *
 * <p>Distinto de {@link DuplicateSkuException} porque a saída é outra: o SKU não está "em uso",
 * está reservado. Liberá-lo para um cadastro novo misturaria o histórico antigo (vendas,
 * movimentações, compras gravam o SKU como texto) ao produto novo.</p>
 */
public class SkuArchivedException extends RuntimeException {
    public SkuArchivedException(String sku) {
        super("Este SKU pertence a um produto excluído — restaure-o no filtro Excluídos (" + sku + ")");
    }
}
