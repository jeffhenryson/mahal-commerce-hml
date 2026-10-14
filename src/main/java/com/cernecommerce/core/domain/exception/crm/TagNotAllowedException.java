package com.cernecommerce.core.domain.exception.crm;

/**
 * PDV-F046 — quem só tem {@code CRM_CUSTOMER_VIP_MANAGE} (o vendedor) mexe apenas na tag VIP. Criar,
 * associar ou remover outra tag exige {@code CRM_CUSTOMER_MANAGE}. Vira 403 {@code TAG_NOT_ALLOWED}.
 */
public class TagNotAllowedException extends RuntimeException {

    public TagNotAllowedException(String nome) {
        super("Sem permissão para a tag \"" + nome + "\": o vendedor só marca ou desmarca a tag VIP.");
    }
}
