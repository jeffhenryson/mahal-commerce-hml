package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Transferência de um SKU entre depósitos, a preço de custo (EST-F039) — ex.: o Salão trazendo
 * latas de essência da loja.
 *
 * <p>{@code quantity} é inteiro, ao contrário da conversão: é a lata lacrada que muda de lugar, e
 * meia lata não existe na prateleira.</p>
 */
@Data
public class StockTransferRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Schema(example = "ZOMO-50G-MENTA", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sku;

    @NotBlank
    @Size(min = 2, max = 50)
    @Schema(example = "PRINCIPAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fromWarehouseCode;

    @NotBlank
    @Size(min = 2, max = 50)
    @Schema(example = "SALAO", requiredMode = Schema.RequiredMode.REQUIRED)
    private String toWarehouseCode;

    @NotNull
    @Min(1)
    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;

    // 255 menos a seta e o código do outro depósito, que o service acrescenta a cada ponta.
    @NotBlank
    @Size(max = 200)
    @Schema(example = "Consumo do Salão (transferência a custo)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
