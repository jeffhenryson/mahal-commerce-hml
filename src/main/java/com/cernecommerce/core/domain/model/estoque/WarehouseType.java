package com.cernecommerce.core.domain.model.estoque;

/**
 * Tipo de depósito de estoque: loja física, centro de distribuição do e-commerce ou o Salão.
 *
 * <p>{@link #SALAO} é o depósito das essências de sessão (narguilé nas mesas). Só pode haver um
 * ativo, e a existência dele muda de onde a sessão consome a lata: ver
 * {@code EstoqueUseCase.consumeSession}.</p>
 */
public enum WarehouseType {
    LOJA_FISICA,
    ECOMMERCE,
    SALAO
}
