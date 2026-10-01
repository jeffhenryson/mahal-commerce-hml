package com.cernecommerce.core.domain.exception.pdv;

/**
 * Sessão de narguilé sem essência — no lançamento, ou o 2º rosh do duplo sem o sabor dele (PDV-C031).
 *
 * <p>A essência é o que a casa quer saber depois ("qual sabor saiu naquela mesa"), e na sessão do
 * cardápio ela é texto livre, sem produto por trás. Antes era {@code IllegalArgumentException}, que
 * o handler global achata num 400 "Requisição inválida" indistinguível de um corpo malformado.</p>
 */
public class SessionEssenceRequiredException extends RuntimeException {
    public SessionEssenceRequiredException(String campo) {
        super(campo + " é obrigatória na sessão");
    }
}
