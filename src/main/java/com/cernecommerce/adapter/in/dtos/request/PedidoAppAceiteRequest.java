package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Aceite de um pedido do app no caixa (ECM-F010). */
@Data
public class PedidoAppAceiteRequest {

    @NotNull
    @Schema(description = "O caixa aberto do próprio operador, que passa a atender o pedido.", example = "42")
    private Long sessionId;

    @DecimalMin("0.0")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Obrigatória na ENTREGA (zero vale, se for grátis); proibida na RETIRADA. Entra no "
            + "total a pagar e o cliente a vê no app.", example = "8.00")
    private BigDecimal deliveryFee;
}
