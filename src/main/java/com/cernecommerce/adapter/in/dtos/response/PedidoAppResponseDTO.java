package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** Um pedido da fila "Pedidos do app" do PDV (ECM-F010). */
@Data
public class PedidoAppResponseDTO {

    @Schema(description = "O pedido, com itens, entrega e totalPayable (já com a taxa definida no aceite).")
    private OrderResponseDTO pedido;

    private AtendimentoResponseDTO atendimento;

    private Long clienteId;
    private String clienteNome;

    @Schema(description = "Telefone do cliente — é por ele que a loja manda o Pix da entrega.")
    private String clienteContato;

    private String clienteEmail;
}
