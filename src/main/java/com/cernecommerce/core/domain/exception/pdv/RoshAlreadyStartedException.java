package com.cernecommerce.core.domain.exception.pdv;

/**
 * PDV-F055 — desfazer o duplo com o 2º rosh já fora da fila. Depois do preparo a essência queimou e
 * nada volta: o caminho é a desistência.
 */
public class RoshAlreadyStartedException extends RuntimeException {
    public RoshAlreadyStartedException(Long roshItemId) {
        super("O 2º rosh já foi ao preparo — use desistência (linha " + roshItemId + ").");
    }
}
