package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionTimeline;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * PDV-F035 — a linha do tempo de uma sessão da mesa, em {@code GET /pdv/comandas/{id}}. Durações em
 * minutos; fase que ainda não terminou vem nula.
 */
@Schema(description = "Linha do tempo de uma sessão (SESSAO ou ROSH_EXTRA) da mesa — PDV-F035")
public record ComandaSessionTimelineDTO(
        Long itemId,
        ConsumptionMode mode,
        @Schema(description = "Sessão a que o rosh extra pertence. Nulo na própria sessão.") Long linkedItemId,
        String productName,
        @Schema(description = "Foi ao salão antes de paga (PDV-F034).") boolean pagarNoFinal,
        SessionStatus status,
        @Schema(description = "Recolhida sem ter sido entregue (PREPARANDO → RECOLHIDO).") boolean desistida,
        @Schema(description = "PAGAMENTO, FILA (2º rosh esperando o narguilé) ou nulo (paga no final).")
        SessionTimeline.Espera esperouPor,
        Instant lancadaEm,
        @Schema(description = "Conclusão do pedido que cobrou a linha. Nulo enquanto a receber.") Instant pagaEm,
        Instant inicioEm,
        Instant entregueEm,
        Instant recolhidaEm,
        @Schema(description = "lancadaEm → inicioEm") Long esperaMin,
        @Schema(description = "inicioEm → entregueEm (ou recolhidaEm, se desistida)") Long preparoMin,
        @Schema(description = "entregueEm → recolhidaEm") Long naMesaMin,
        @Schema(description = "lancadaEm → recolhidaEm") Long totalMin) {

    public static ComandaSessionTimelineDTO of(SessionTimeline t) {
        return new ComandaSessionTimelineDTO(t.itemId(), t.mode(), t.linkedItemId(), t.productName(),
                t.pagarNoFinal(), t.status(), t.desistida(), t.esperouPor(), t.lancadaEm(), t.pagaEm(),
                t.inicioEm(), t.entregueEm(), t.recolhidaEm(), t.esperaMin(), t.preparoMin(), t.naMesaMin(),
                t.totalMin());
    }
}
