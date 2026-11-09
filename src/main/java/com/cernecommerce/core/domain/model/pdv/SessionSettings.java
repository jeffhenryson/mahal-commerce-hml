package com.cernecommerce.core.domain.model.pdv;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuração do cardápio de sessão (PDV-F021): qual utensílio é o vaso padrão e qual é o grande,
 * quanto custa o upgrade, em quais dias da semana o 2º rosh sai de graça ("duplo rosh") e quanto o
 * carvão jumbo soma à sessão (V168). PDV-F061 (V191): o preço da sessão com itens pessoais e o
 * desconto da sessão com compra de essência.
 */
public record SessionSettings(String vasoPadraoCodigo, String vasoGrandeCodigo, BigDecimal upgradeVasoGrandePreco,
        Set<DayOfWeek> diasDuploRosh, BigDecimal upgradeCarvaoJumboPreco, BigDecimal itensPessoaisPreco,
        BigDecimal compraEssenciaDesconto) {

    /** Padrões da V191: R$ 60 com itens pessoais e R$ 5 de desconto na compra de essência. */
    public static final BigDecimal ITENS_PESSOAIS_PRECO_PADRAO = new BigDecimal("60.00");
    public static final BigDecimal COMPRA_ESSENCIA_DESCONTO_PADRAO = new BigDecimal("5.00");

    public SessionSettings {
        vasoPadraoCodigo = normalizeCode(vasoPadraoCodigo);
        vasoGrandeCodigo = normalizeCode(vasoGrandeCodigo);
        if (upgradeVasoGrandePreco == null || upgradeVasoGrandePreco.signum() < 0) {
            throw new IllegalArgumentException("preço do upgrade de vaso é obrigatório e não pode ser negativo");
        }
        diasDuploRosh = diasDuploRosh == null || diasDuploRosh.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(DayOfWeek.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(diasDuploRosh));
        if (upgradeCarvaoJumboPreco == null) {
            upgradeCarvaoJumboPreco = BigDecimal.ZERO;
        }
        if (upgradeCarvaoJumboPreco.signum() < 0) {
            throw new IllegalArgumentException("preço do carvão jumbo não pode ser negativo");
        }
        if (itensPessoaisPreco == null) {
            itensPessoaisPreco = ITENS_PESSOAIS_PRECO_PADRAO;
        }
        if (itensPessoaisPreco.signum() < 0) {
            throw new IllegalArgumentException("preço da sessão com itens pessoais não pode ser negativo");
        }
        if (compraEssenciaDesconto == null) {
            compraEssenciaDesconto = COMPRA_ESSENCIA_DESCONTO_PADRAO;
        }
        if (compraEssenciaDesconto.signum() < 0) {
            throw new IllegalArgumentException("desconto da compra de essência não pode ser negativo");
        }
    }

    /** Sem os preços das modalidades (PDV-F061): valem os padrões da V191. */
    public SessionSettings(String vasoPadraoCodigo, String vasoGrandeCodigo, BigDecimal upgradeVasoGrandePreco,
            Set<DayOfWeek> diasDuploRosh, BigDecimal upgradeCarvaoJumboPreco) {
        this(vasoPadraoCodigo, vasoGrandeCodigo, upgradeVasoGrandePreco, diasDuploRosh, upgradeCarvaoJumboPreco,
                null, null);
    }

    /** Sem o preço do carvão jumbo: jumbo sai de graça, como antes de V168. */
    public SessionSettings(String vasoPadraoCodigo, String vasoGrandeCodigo, BigDecimal upgradeVasoGrandePreco,
            Set<DayOfWeek> diasDuploRosh) {
        this(vasoPadraoCodigo, vasoGrandeCodigo, upgradeVasoGrandePreco, diasDuploRosh, BigDecimal.ZERO);
    }

    /** Quanto o carvão soma ao preço da sessão: só o jumbo tem acréscimo. */
    public BigDecimal charcoalSurcharge(Charcoal carvao) {
        return charcoalSurcharge(carvao, false);
    }

    /** PDV-F057 — idem, mas o jumbo de cortesia não soma nada. */
    public BigDecimal charcoalSurcharge(Charcoal carvao, boolean courtesy) {
        return carvao == Charcoal.JUMBO && !courtesy ? upgradeCarvaoJumboPreco : BigDecimal.ZERO;
    }

    public static SessionSettings defaults() {
        return new SessionSettings(null, null, BigDecimal.ZERO, Set.of());
    }

    public boolean isDuploRoshDay(DayOfWeek day) {
        return diasDuploRosh.contains(day);
    }

    /** Forma persistida de {@link #diasDuploRosh}: nomes de {@link DayOfWeek} separados por vírgula. */
    public String diasDuploRoshAsText() {
        return diasDuploRosh.stream().sorted().map(DayOfWeek::name).collect(Collectors.joining(","));
    }

    public static Set<DayOfWeek> parseDias(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> DayOfWeek.valueOf(s.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DayOfWeek.class)));
    }

    private static String normalizeCode(String code) {
        return code == null || code.isBlank() ? null : code.trim().toUpperCase(Locale.ROOT);
    }
}
