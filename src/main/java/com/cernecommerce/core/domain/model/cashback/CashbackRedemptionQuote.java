package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;

/**
 * Prévia de "quanto o cliente pode usar nesta compra".
 *
 * @param available      saldo disponível agora.
 * @param minimumBalance saldo mínimo configurado para resgatar.
 * @param capPercent     teto configurado, em % da mercadoria.
 * @param capAmount      teto em reais para esta compra ({@code merchandiseNet * capPercent / 100}).
 * @param maxRedeemable  o máximo resgatável agora: {@code min(available, capAmount)}, ou 0 quando
 *                       {@code reason} não é nulo.
 * @param reason         código do motivo de não poder resgatar (mesmo código do erro 422), ou
 *                       {@code null} quando pode.
 */
public record CashbackRedemptionQuote(BigDecimal available, BigDecimal minimumBalance, BigDecimal capPercent,
        BigDecimal capAmount, BigDecimal maxRedeemable, String reason) {
}
