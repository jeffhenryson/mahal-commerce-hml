package com.cernecommerce.core.domain.model.compras;

/**
 * O que o operador ensinou sobre um produto de um fornecedor (V182), pela chave fornecedor + código
 * do produto na nota ({@code cProd}): o fator de embalagem (quantas unidades de estoque vêm em uma
 * unidade comercial da nota) e, opcionalmente, o SKU — para nota sem EAN.
 */
public record SupplierProductMapping(Long supplierId, String supplierProductCode, String sku, int unitsPerPackage) {

    public SupplierProductMapping {
        if (supplierId == null) {
            throw new IllegalArgumentException("supplierId é obrigatório");
        }
        if (supplierProductCode == null || supplierProductCode.isBlank()) {
            throw new IllegalArgumentException("supplierProductCode é obrigatório");
        }
        if (unitsPerPackage < 1) {
            throw new IllegalArgumentException("unitsPerPackage deve ser pelo menos 1");
        }
    }
}
