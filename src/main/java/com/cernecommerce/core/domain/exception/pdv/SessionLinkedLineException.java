package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F061 — o pacote da compra de essência não sai sozinho da mesa: ele sai junto com a sessão.
 * 409 SESSION_LINKED_LINE.
 */
public class SessionLinkedLineException extends RuntimeException {

    public SessionLinkedLineException(Long itemId) {
        super("A linha " + itemId + " é o pacote de uma sessão: remova a sessão");
    }
}
