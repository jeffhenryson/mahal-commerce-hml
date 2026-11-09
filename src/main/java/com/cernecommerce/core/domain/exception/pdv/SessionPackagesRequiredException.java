package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — compra de essência e itens pessoais exigem pelo menos 1 pacote da loja.
 * 400 SESSION_PACKAGES_REQUIRED.
 */
public class SessionPackagesRequiredException extends RuntimeException {

    public SessionPackagesRequiredException(String modalidade) {
        super("A modalidade " + modalidade + " exige pelo menos 1 pacote da loja");
    }
}
