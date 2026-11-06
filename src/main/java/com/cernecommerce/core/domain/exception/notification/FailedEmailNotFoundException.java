package com.cernecommerce.core.domain.exception.notification;

public class FailedEmailNotFoundException extends RuntimeException {
    public FailedEmailNotFoundException(Long id) {
        super("E-mail não encontrado: id=" + id);
    }
}
