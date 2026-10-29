package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

import java.math.BigDecimal;

/** Aceite de um pedido do app pela Central de Pedidos (ECM-F012). O corpo inteiro é opcional. */
@Data
public class OrderAcceptRequest {

    @Schema(description = "Caixa aberto do próprio operador. Omitido, vale o caixa aberto de quem aceita "
            + "(404 NO_OPEN_SESSION se não houver).", example = "42")
    private Long sessionId;

    @DecimalMin("0.0")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Obrigatória na ENTREGA (zero vale, se for grátis); proibida na RETIRADA. Entra no "
            + "total a pagar e o cliente a vê no app.", example = "8.00")
    private BigDecimal deliveryFee;
}
