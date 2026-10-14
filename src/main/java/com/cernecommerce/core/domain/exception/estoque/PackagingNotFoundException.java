package com.cernecommerce.core.domain.exception.estoque;

/** O SKU não está dentro de nenhuma embalagem (EST-F032). */
public class PackagingNotFoundException extends RuntimeException {
    public PackagingNotFoundException(String childSku) {
        super("SKU " + childSku + " não está ligado a nenhuma embalagem");
    }

    private PackagingNotFoundException(String message, boolean ignored) {
        super(message);
    }

    /** PDV-F045 — abrir à mão um SKU que não contém nada (não é pai de nenhuma ligação). */
    public static PackagingNotFoundException containsNothing(String parentSku) {
        return new PackagingNotFoundException("SKU " + parentSku + " não contém nenhuma embalagem para abrir", true);
    }
}
