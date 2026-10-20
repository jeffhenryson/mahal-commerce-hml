package com.cernecommerce.core.domain.model.salao;

/**
 * Como o aparelho vê a mesa: livre (qualquer um pede), ocupada com acesso (é dele ou entrou pelo
 * código) ou ocupada sem acesso (precisa do código).
 */
public enum AcessoMesa {
    LIVRE,
    LIBERADO,
    BLOQUEADO
}
