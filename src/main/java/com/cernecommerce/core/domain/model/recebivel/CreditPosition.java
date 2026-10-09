package com.cernecommerce.core.domain.model.recebivel;

import java.math.BigDecimal;

/**
 * Limites (já com os padrões) e saldos em aberto de um cliente nos dois canais do "Marcar" (CRM-F010).
 * Canal {@code null} nos métodos = o cliente inteiro.
 *
 * <p>Era um {@code record} aninhado em {@code ReceivableService}, e o
 * {@code HexagonalArchitectureTest} barra classe em {@code core.service} que não implementa port: a
 * posição de crédito é conceito de domínio, então mora aqui, ao lado de {@link CreditLimits}.</p>
 */
public record CreditPosition(CreditLimits own, BigDecimal limitBalcao, BigDecimal limitMesa,
        BigDecimal openBalcao, BigDecimal openMesa) {

    public BigDecimal openTotal() {
        return openBalcao.add(openMesa);
    }

    public BigDecimal open(OnAccountChannel channel) {
        if (channel == null) {
            return openTotal();
        }
        return channel == OnAccountChannel.BALCAO ? openBalcao : openMesa;
    }

    /** No total: o teto, se houver; senão, a soma dos dois canais. */
    public BigDecimal limit(OnAccountChannel channel) {
        if (channel == null) {
            return own.total() != null ? own.total() : limitBalcao.add(limitMesa);
        }
        return channel == OnAccountChannel.BALCAO ? limitBalcao : limitMesa;
    }

    /** O que cabe: o do canal, sem passar do que sobra no teto total. */
    public BigDecimal available(OnAccountChannel channel) {
        BigDecimal fit = channel == null
                ? available(limitBalcao, openBalcao).add(available(limitMesa, openMesa))
                : available(limit(channel), open(channel));
        return own.total() == null ? fit : fit.min(available(own.total(), openTotal()));
    }

    private static BigDecimal available(BigDecimal limit, BigDecimal open) {
        return limit.subtract(open).max(BigDecimal.ZERO);
    }
}
