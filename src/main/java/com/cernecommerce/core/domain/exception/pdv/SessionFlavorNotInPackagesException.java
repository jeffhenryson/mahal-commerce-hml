package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — o sabor da sessão (essenciaSku/essenciaSku2) tem de ser um dos pacotes comprados.
 * 400 SESSION_FLAVOR_NOT_IN_PACKAGES.
 */
public class SessionFlavorNotInPackagesException extends RuntimeException {

    public SessionFlavorNotInPackagesException(String sku) {
        super("O sabor " + sku + " não está entre os pacotes da sessão");
    }
}
