package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;

/**
 * Um ganho e a parte dele que ainda não foi consumida por resgate ({@code remaining}). Unidade da
 * alocação FIFO do resgate e da expiração parcial.
 */
public record RedeemableEarning(CashbackEntry earned, BigDecimal remaining) {
}
