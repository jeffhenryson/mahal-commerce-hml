package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** A ligação de embalagem gravada (EST-F032). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PackagingResponseDTO {

    @Schema(example = "LM-AZUL-UN")
    private String childSku;

    @Schema(example = "LM-AZUL-MACO")
    private String parentSku;

    @Schema(example = "20")
    private int unitsPerParent;
}
