package com.cernecommerce.core.domain.exception.pdv;

/**
 * Venda de balcão sem item avulso e sem kit montável. Antes de {@code SaleRequest.kits} isso era o
 * {@code @NotEmpty} de {@code items}; como uma venda só de kit é válida, a checagem passou a olhar os dois.
 */
public class SaleEmptyException extends RuntimeException {
    public SaleEmptyException() {
        super("A venda precisa de ao menos um item ou kit");
    }
}
