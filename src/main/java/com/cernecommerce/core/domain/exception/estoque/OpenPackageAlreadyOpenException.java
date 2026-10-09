package com.cernecommerce.core.domain.exception.estoque;

/**
 * Já há lata em uso para o par SKU/depósito (EST-F033) — cadastrar outra criaria duas verdades
 * para a mesma prateleira. Para "esta acabou e abri outra", o caminho é o {@code /replace}.
 */
public class OpenPackageAlreadyOpenException extends RuntimeException {
    public OpenPackageAlreadyOpenException(String sku, String warehouseCode) {
        super("Já existe lata aberta de " + sku + " no depósito " + warehouseCode
                + ": para trocar por outra, use Repor essência");
    }
}
