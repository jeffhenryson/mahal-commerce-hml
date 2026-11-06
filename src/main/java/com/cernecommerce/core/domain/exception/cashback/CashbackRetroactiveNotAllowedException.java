package com.cernecommerce.core.domain.exception.cashback;

/** O crédito retroativo não pode ser feito (422). O {@link #code()} diz por quê. */
public class CashbackRetroactiveNotAllowedException extends RuntimeException {

    public static final String CUSTOMER_NOT_ELIGIBLE = "CASHBACK_CUSTOMER_NOT_ELIGIBLE";
    public static final String NOTHING_TO_CREDIT = "CASHBACK_NOTHING_TO_CREDIT";
    public static final String GLOBAL_RATE_MISSING = "CASHBACK_GLOBAL_RATE_MISSING";

    private final String code;

    public CashbackRetroactiveNotAllowedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
