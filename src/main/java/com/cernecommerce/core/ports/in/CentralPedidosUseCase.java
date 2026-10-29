package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Configuração do recebimento, status público da loja e indicadores do dia da Central de Pedidos (ECM-F013). */
public interface CentralPedidosUseCase {

    /** A configuração em vigor; o padrão enquanto a loja nunca salvou. */
    CentralPedidosConfig config();

    /** Substitui a configuração inteira. */
    CentralPedidosConfig salvarConfig(CentralPedidosConfig config, String username);

    /** O que o app (frontend-market) precisa saber antes do checkout. */
    StatusLoja statusLoja();

    /** Indicadores do dia local da loja (America/Sao_Paulo). */
    Indicadores indicadores(LocalDate dia);

    record StatusLoja(boolean aberto, boolean appRecebendo, boolean entregaHabilitada, boolean retiradaHabilitada,
            int tempoPreparoMin, int tempoEntregaMin, BigDecimal pedidoMinimo, BigDecimal taxaFixa,
            List<CentralPedidosConfig.TaxaBairro> bairros) {
    }

    record Indicadores(List<PorCanal> porCanal, Pendentes pendentesAgora, Recusas recusas,
            List<Cancelamentos> cancelamentos, Map<String, BigDecimal> taxaRecusa) {
    }

    record PorCanal(String channel, long pedidos, BigDecimal receitaLiquida, BigDecimal ticketMedio) {
    }

    record Pendentes(long pedidosApp, long solicitacoes, long chamados) {
    }

    record Recusas(List<RecusaApp> pedidosApp, List<RecusaQr> solicitacoes) {
    }

    record RecusaApp(String reasonCode, long total) {
    }

    /** {@code motivo} nulo quando a recusa veio sem texto. */
    record RecusaQr(String motivo, long total) {
    }

    record Cancelamentos(String channel, long total, BigDecimal valor) {
    }
}
