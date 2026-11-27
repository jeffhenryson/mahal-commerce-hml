package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Vínculo com o catálogo. {@code sku: null} desvincula. */
@Data
public class ProductRequestLinkSkuRequest {

    @Size(max = 100)
    @Schema(description = "SKU do produto ou da variação; null desvincula", nullable = true)
    private String sku;
}
