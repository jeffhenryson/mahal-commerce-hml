package com.cernecommerce.core.domain.exception.estoque;

/** Solicitação sem cliente do CRM e sem nome + telefone do avulso — 400 {@code CUSTOMER_REQUIRED}. */
public class ProductRequestCustomerRequiredException extends RuntimeException {
    public ProductRequestCustomerRequiredException() {
        super("Informe o cliente do CRM ou o nome e o telefone de quem pediu");
    }
}
