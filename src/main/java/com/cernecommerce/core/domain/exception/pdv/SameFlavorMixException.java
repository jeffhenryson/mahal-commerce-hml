package com.cernecommerce.core.domain.exception.pdv;

/** PDV-F051 — sessão mesclada com o mesmo sabor nas duas posições: não é mescla. */
public class SameFlavorMixException extends RuntimeException {
    public SameFlavorMixException(String sku) {
        super("Os dois sabores da mescla são o mesmo: " + sku);
    }
}
