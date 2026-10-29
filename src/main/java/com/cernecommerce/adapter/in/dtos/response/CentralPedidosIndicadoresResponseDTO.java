package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Indicadores do dia da Central de Pedidos (ECM-F013) — {@code GET /central-pedidos/indicators}. */
@Schema(name = "CentralPedidosIndicadores")
public record CentralPedidosIndicadoresResponseDTO(
        @Schema(description = "MARKETPLACE e MESA, sempre os dois e nesta ordem.") List<PorCanal> porCanal,
        Pendentes pendentesAgora,
        Recusas recusas,
        @Schema(description = "Só canais com cancelamento no dia.") List<Cancelamentos> cancelamentos,
        @Schema(description = "Recusados ÷ recebidos no dia, como fração (0.045 = 4,5%), por canal.")
        Map<String, BigDecimal> taxaRecusa) {

    @Schema(name = "CentralPedidosPorCanal")
    public record PorCanal(String channel, long pedidos,
            @Schema(description = "Mesma regra do totalRevenueNet de /orders/summary.") BigDecimal receitaLiquida,
            BigDecimal ticketMedio) {
    }

    @Schema(name = "CentralPedidosPendentes")
    public record Pendentes(long pedidosApp, long solicitacoes, long chamados) {
    }

    @Schema(name = "CentralPedidosRecusas")
    public record Recusas(List<RecusaApp> pedidosApp, List<RecusaQr> solicitacoes) {
    }

    @Schema(name = "CentralPedidosRecusaApp")
    public record RecusaApp(@Schema(description = "Recusa antiga, sem código, conta como OUTRO.") String reasonCode,
            long total) {
    }

    @Schema(name = "CentralPedidosRecusaQr")
    public record RecusaQr(@Schema(description = "Texto livre; nulo quando a recusa veio sem motivo.") String motivo,
            long total) {
    }

    @Schema(name = "CentralPedidosCancelamentos")
    public record Cancelamentos(String channel, long total, BigDecimal valor) {
    }
}
