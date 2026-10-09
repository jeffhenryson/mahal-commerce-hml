package com.cernecommerce.core.domain.exception.pdv;

/**
 * A essência da sessão precisa ser um <b>sabor</b> (PDV-F042): o SKU base de um produto com
 * variações ("Essência Zig", cujos sabores são as variações) não tem lata nem saldo próprios — a
 * lata aberta e o estoque são por sabor.
 */
public class EssenceMustBeFlavorException extends RuntimeException {
    public EssenceMustBeFlavorException(String sku) {
        super("Escolha o sabor da essência, não o produto base: " + sku + " tem variações");
    }
}
