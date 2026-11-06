package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** {@code expectedCreditAmount}: o valor que o admin viu na prévia. O crédito é recalculado no servidor. */
public record CashbackRetroactiveRequest(@NotNull BigDecimal expectedCreditAmount) {
}
