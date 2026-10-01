package com.cernecommerce.core.domain.exception.recebivel;

/** Cliente sem a tag VIP. */
public class CustomerNotEligibleForOnAccountException extends RuntimeException {

    public CustomerNotEligibleForOnAccountException(Long customerId) {
        super("O cliente " + customerId + " não é VIP e não pode marcar");
    }
}
