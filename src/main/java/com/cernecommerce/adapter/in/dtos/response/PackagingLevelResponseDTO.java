package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Um nível da cadeia de embalagem, da mais externa para a mais interna (EST-F032). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackagingLevelResponseDTO {

    @Schema(example = "LM-AZUL-MACO")
    private String sku;

    @Schema(description = "O nível de dentro; nulo no mais interno.", example = "LM-AZUL-UN")
    private String containsSku;

    @Schema(description = "Quantos do nível de dentro este contém; nulo no mais interno.", example = "20")
    private Integer containsUnits;

    @Schema(description = "Disponível no depósito pedido; nulo sem warehouseCode.", example = "8")
    private BigDecimal available;
}
