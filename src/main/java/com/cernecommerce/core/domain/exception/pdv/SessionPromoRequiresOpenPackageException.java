package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F062 — a sessão "Promo de essência" só usa lata já aberta no Salão: o sabor não tem lata aberta
 * (ou não é vendido por lata). A promo não abre lata nova nem tira da loja.
 * 409 SESSION_PROMO_REQUIRES_OPEN_PACKAGE.
 */
public class SessionPromoRequiresOpenPackageException extends RuntimeException {

    private final String sku;

    public SessionPromoRequiresOpenPackageException(String sku) {
        super("A essência " + sku + " não tem lata aberta no Salão: a promo só usa lata já aberta");
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
