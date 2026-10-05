package com.cernecommerce.core.domain.exception.email;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String cause) {
        super("Failed to send email: " + cause);
    }
}
