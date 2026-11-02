package com.cernecommerce.core.domain.model.crm;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Agregação de métricas do CRM para o dashboard overview.
 *
 * <p>Todos os números são reais desde a pendência 8: {@code ltvMedio} é a média do LTV de quem já
 * comprou, {@code disparosWhatsappMes} conta os disparos ENVIADO por WhatsApp no mês corrente (fuso da
 * loja), {@code porSegmento} vem do segmento materializado (V183) e {@code cashbackEmAberto} é o saldo
 * disponível da base inteira.</p>
 */
public record CrmDashboardOverview(
    long totalClientes,
    long clientesAtivos,
    BigDecimal ltvMedio,
    long disparosWhatsappMes,
    Map<String, Long> porSegmento,
    Map<CustomerStage, Long> porEstagio,
    BigDecimal cashbackEmAberto
) {
    /** Sem o cashback em aberto (anterior à pendência 8). */
    public CrmDashboardOverview(long totalClientes, long clientesAtivos, BigDecimal ltvMedio, long disparosWhatsappMes,
            Map<String, Long> porSegmento, Map<CustomerStage, Long> porEstagio) {
        this(totalClientes, clientesAtivos, ltvMedio, disparosWhatsappMes, porSegmento, porEstagio, BigDecimal.ZERO);
    }
}
