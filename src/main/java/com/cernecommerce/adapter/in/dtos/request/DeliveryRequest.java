package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Entrega ou retirada da venda (PDV-F022). Na venda, {@code type} é obrigatório; no
 * {@code PATCH /orders/{id}/delivery} todos os campos são opcionais, e {@code type}/{@code fee}
 * não podem mudar.
 */
@Data
public class DeliveryRequest {

    @Schema(description = "RETIRADA ou ENTREGA. ENTREGA grava RESERVADO; RETIRADA grava CONCLUIDO, ou RESERVADO "
            + "se a venda vier com reserveForPickup=true.", example = "ENTREGA")
    private DeliveryType type;

    @Valid
    @Schema(description = "Obrigatório em ENTREGA; proibido em RETIRADA.")
    private DeliveryAddressRequest address;

    @Schema(description = "Quem leva a ENTREGA.", example = "MOTOBOY_LOJA")
    private DeliveryMethod method;

    @Size(max = 120)
    @Schema(description = "MOTOBOY_LOJA — nome do entregador.")
    private String courierName;

    @Size(max = 30)
    @Schema(description = "MOTOBOY_LOJA — telefone do entregador.")
    private String courierPhone;

    @Size(max = 60)
    @Schema(description = "APP_99 — código de coleta, opcional.")
    private String pickupCode;

    @Size(max = 60)
    @Schema(description = "APP_99 — código de entrega, opcional.")
    private String dropoffCode;

    @Size(max = 60)
    @Schema(description = "CORREIOS — código de rastreio.")
    private String trackingCode;

    @DecimalMin("0.0")
    @Digits(integer = 12, fraction = 2)
    @Schema(description = "Taxa de entrega cobrada do cliente. Somada ao total a pagar (fora do "
            + "líquido). Congelada na venda — não aceita no PATCH.", example = "8.00")
    private BigDecimal fee;
}
