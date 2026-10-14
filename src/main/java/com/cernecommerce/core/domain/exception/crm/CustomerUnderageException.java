package com.cernecommerce.core.domain.exception.crm;

/** CRM-F012 — data de nascimento de quem tem menos de 18 anos: a tabacaria não cadastra menor. */
public class CustomerUnderageException extends RuntimeException {

    public CustomerUnderageException(int idade) {
        super("Cliente com " + idade + " anos: o cadastro exige 18 anos ou mais.");
    }
}
