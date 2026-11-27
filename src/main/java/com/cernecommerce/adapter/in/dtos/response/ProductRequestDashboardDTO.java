package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** KPIs da aba Pedidos de clientes. */
public record ProductRequestDashboardDTO(
        long pending,
        long ordered,
        long arrived,
        @Schema(description = "Itens ARRIVED com alguma solicitação ativa ainda sem aviso") long awaitingNotification,
        @Schema(description = "Até 5 itens abertos com mais solicitações ativas") List<Top> topRequested) {

    public record Top(long id, String description, int entriesCount) {
    }
}
