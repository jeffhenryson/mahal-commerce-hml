package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Uma família da central de cigarros do PDV (PDV-F041): o produto e as suas cadeias de embalagem. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CigarroFamilyResponseDTO {

    @Schema(example = "LM")
    private String productSku;

    @Schema(example = "LM")
    private String productName;

    @Schema(description = "Uma cadeia por cor, da carteira ao solto.")
    private List<Line> lines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Line {
        private List<Level> levels;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Level {

        @Schema(description = "SKU vendável — é o que vai no item da venda.", example = "LM-AZUL-MACO")
        private String sku;

        @Schema(description = "Atributos da variação.", example = "azul · maço")
        private String label;

        @Schema(description = "Preço efetivo do SKU; nulo sem preço.", example = "12.00")
        private BigDecimal price;

        @Schema(description = "Disponível no depósito. Vender além dele abre a embalagem de fora sozinho (EST-F032).",
                example = "8")
        private BigDecimal available;

        @Schema(description = "O nível de dentro; nulo no solto.", example = "LM-AZUL-UN")
        private String containsSku;

        @Schema(description = "Quantos do nível de dentro este contém; nulo no solto.", example = "20")
        private Integer containsUnits;
    }
}
