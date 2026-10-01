package com.cernecommerce.core.domain.exception.pdv;

/**
 * Modo de sessão do cardápio ({@code SESSAO}/{@code ROSH_EXTRA}) mandado para
 * {@code POST /pdv/comandas/{id}/items} (PDV-C031). A sessão do cardápio não tem produto: entra só por
 * {@code POST /pdv/comandas/{id}/sessoes}, que resolve preço pela faixa e aloca os utensílios.
 */
public class MenuSessionNotAllowedOnItemsException extends RuntimeException {
    public MenuSessionNotAllowedOnItemsException(String mode) {
        super("Modo " + mode + " é sessão do cardápio: lance por POST /pdv/comandas/{id}/sessoes, não por /items");
    }
}
