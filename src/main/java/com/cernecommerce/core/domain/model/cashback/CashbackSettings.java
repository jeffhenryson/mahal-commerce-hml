package com.cernecommerce.core.domain.model.cashback;

import java.math.BigDecimal;

/**
 * Parâmetros do programa de cashback que o admin ajusta manualmente (gravados em
 * {@code system_config}).
 *
 * @param carenciaDias       dias entre o pagamento e o ganho entrar no saldo disponível (0–365).
 * @param expiracaoDias      dias, a partir da liberação, até o ganho vencer (1–3650).
 * @param resgateTetoPercent teto do resgate em % do valor da mercadoria (0–100). 0 desliga o resgate.
 * @param resgateMinimoSaldo saldo disponível mínimo para o cliente poder resgatar (&ge; 0).
 *
 * <p>Carência e expiração valem para ganhos <b>novos</b>: cada ganho já lançado guarda as próprias
 * datas.</p>
 */
public record CashbackSettings(int carenciaDias, int expiracaoDias, BigDecimal resgateTetoPercent,
        BigDecimal resgateMinimoSaldo) {

    public static final String KEY_CARENCIA_DIAS = "cashback.carencia.dias";
    public static final String KEY_EXPIRACAO_DIAS = "cashback.expiracao.dias";
    public static final String KEY_RESGATE_TETO_PERCENT = "cashback.resgate.teto.percent";
    public static final String KEY_RESGATE_MINIMO_SALDO = "cashback.resgate.minimo.saldo";

    public static final int DEFAULT_CARENCIA_DIAS = 7;
    public static final int DEFAULT_EXPIRACAO_DIAS = 180;
    public static final BigDecimal DEFAULT_RESGATE_TETO_PERCENT = new BigDecimal("50");
    public static final BigDecimal DEFAULT_RESGATE_MINIMO_SALDO = new BigDecimal("50.00");

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public CashbackSettings {
        if (carenciaDias < 0 || carenciaDias > 365) {
            throw new IllegalArgumentException("carenciaDias deve estar entre 0 e 365");
        }
        if (expiracaoDias < 1 || expiracaoDias > 3650) {
            throw new IllegalArgumentException("expiracaoDias deve estar entre 1 e 3650");
        }
        if (resgateTetoPercent == null || resgateTetoPercent.signum() < 0
                || resgateTetoPercent.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("resgateTetoPercent deve estar entre 0 e 100");
        }
        if (resgateMinimoSaldo == null || resgateMinimoSaldo.signum() < 0) {
            throw new IllegalArgumentException("resgateMinimoSaldo não pode ser negativo");
        }
    }

    public static CashbackSettings defaults() {
        return new CashbackSettings(DEFAULT_CARENCIA_DIAS, DEFAULT_EXPIRACAO_DIAS,
                DEFAULT_RESGATE_TETO_PERCENT, DEFAULT_RESGATE_MINIMO_SALDO);
    }

    /** Teto 0 desliga o resgate em todos os canais. */
    public boolean redemptionEnabled() {
        return resgateTetoPercent.signum() > 0;
    }
}
