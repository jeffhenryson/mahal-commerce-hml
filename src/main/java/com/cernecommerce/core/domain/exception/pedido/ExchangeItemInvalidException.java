package com.cernecommerce.core.domain.exception.pedido;

/**
 * PED-F006 — o item a devolver ou o item a entregar não serve para a troca: o SKU não está no pedido,
 * a quantidade passa do que resta (vendida − já estornada), a linha não é de catálogo ou o novo SKU
 * não é vendido por unidade. 422: o pedido chegou bem formado, o conteúdo é que não fecha.
 */
public class ExchangeItemInvalidException extends RuntimeException {
    public ExchangeItemInvalidException(String message) {
        super(message);
    }
}
