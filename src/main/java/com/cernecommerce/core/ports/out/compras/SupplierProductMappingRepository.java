package com.cernecommerce.core.ports.out.compras;

import com.cernecommerce.core.domain.model.compras.SupplierProductMapping;

import java.util.Collection;
import java.util.Map;

/** Port de saída do mapeamento fornecedor + cProd (V182). */
public interface SupplierProductMappingRepository {

    /** Mapeamentos do fornecedor para os códigos pedidos, indexados pelo código. */
    Map<String, SupplierProductMapping> findBySupplierAndCodes(Long supplierId, Collection<String> supplierProductCodes);

    /** Grava ou substitui o mapeamento da chave fornecedor + código. */
    void upsert(SupplierProductMapping mapping, String username);
}
