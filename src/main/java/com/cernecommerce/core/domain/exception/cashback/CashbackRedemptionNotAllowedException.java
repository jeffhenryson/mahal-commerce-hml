package com.cernecommerce.core.domain.exception.cashback;

/**
 * O resgate de cashback pedido não cabe nas regras do programa (422). O {@link #code()} diz qual
 * regra falhou, para a tela mostrar a mensagem certa — o mesmo código que a prévia
 * ({@code CashbackRedemptionQuote.reason}) devolve.
 */
public class CashbackRedemptionNotAllowedException extends RuntimeException {

    public static final String CUSTOMER_REQUIRED = "CASHBACK_CUSTOMER_REQUIRED";
    public static final String CUSTOMER_NOT_ELIGIBLE = "CASHBACK_CUSTOMER_NOT_ELIGIBLE";
    public static final String INSUFFICIENT_BALANCE = "CASHBACK_INSUFFICIENT_BALANCE";
    public static final String BELOW_MINIMUM_BALANCE = "CASHBACK_BELOW_MINIMUM_BALANCE";
    public static final String ABOVE_CAP = "CASHBACK_ABOVE_CAP";
    public static final String REDEMPTION_DISABLED = "CASHBACK_REDEMPTION_DISABLED";

    private final String code;

    public CashbackRedemptionNotAllowedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
