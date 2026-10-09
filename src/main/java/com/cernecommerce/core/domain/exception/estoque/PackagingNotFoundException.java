package com.cernecommerce.core.domain.exception.estoque;

/** O SKU não está dentro de nenhuma embalagem (EST-F032). */
public class PackagingNotFoundException extends RuntimeException {
    public PackagingNotFoundException(String childSku) {
        super("SKU " + childSku + " não está ligado a nenhuma embalagem");
    }
}
