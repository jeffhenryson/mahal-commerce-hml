package com.cernecommerce.core.domain.exception.estoque;

/**
 * Transferência com origem e destino no mesmo depósito. Mesma razão de
 * {@link SameSkuConversionException}: seria uma saída e uma entrada que se anulam e ainda deixam
 * duas linhas no ledger descrevendo um movimento que não houve.
 */
public class SameWarehouseTransferException extends RuntimeException {
    public SameWarehouseTransferException(String warehouseCode) {
        super("Transferência exige depósitos distintos: origem e destino são ambos " + warehouseCode);
    }
}
