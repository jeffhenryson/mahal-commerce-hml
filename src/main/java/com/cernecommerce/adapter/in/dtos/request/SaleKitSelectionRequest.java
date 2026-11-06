package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kit na venda de balcão: o corpo de {@link KitSelectionRequest} mais a observação e o desconto de cada
 * linha. Classe separada de propósito: aquela também atende o carrinho público, que não pode mandar desconto.
 */
@Data
public class SaleKitSelectionRequest {

    @NotNull
    private Long templateId;

    @NotNull
    @Size(max = 50)
    @Valid
    private List<Pick> picks;

    @Data
    @Schema(name = "SaleKitPick")
    public static class Pick {
        @NotNull
        private Long stepId;

        @NotBlank
        @Size(max = 50)
        private String sku;

        @Size(max = 200)
        @Schema(description = "Observação da linha, gravada no item do pedido.")
        private String note;

        @DecimalMin(value = "0.0")
        @Schema(description = "Desconto além do desconto do kit. Exige PDV_SALE_DISCOUNT e conta no teto de "
                + "desconto. Não pode passar do valor da linha.", example = "1.50")
        private BigDecimal discountAmount;
    }
}
