package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

/** Corpo do {@code POST /orders/bulk-status} — o "Liberar selecionados/todos" de Vendas › Reservas. */
@Data
public class OrderBulkStatusRequest {

    @NotEmpty
    @Size(max = 200)
    @Schema(description = "Pedidos a mover. Cada um é validado e gravado isoladamente.", example = "[101, 102]")
    private List<@NotNull Long> orderIds;

    @NotNull
    @Schema(description = "Novo estado, aplicado a todos. Segue a mesma máquina de estados do "
            + "POST /orders/{id}/status.", example = "CONCLUIDO")
    private OrderStatus status;
}
