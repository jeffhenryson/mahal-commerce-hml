package com.cernecommerce.core.domain.exception.estoque;

/**
 * O SKU é a base de um produto com variações, e a base não é vendável (EST-F036): o estoque está
 * nas variações ("LM azul", "LM vermelho"), e "LM" sozinho não existe na prateleira. Vale para venda
 * e para entrada de estoque; saída e ajuste continuam, para escoar o que ficou na base por engano.
 */
public class ParentNotSellableException extends RuntimeException {
    public ParentNotSellableException(String sku) {
        super("O produto " + sku + " tem variações: venda e dê entrada nas variações, não no produto base");
    }
}
