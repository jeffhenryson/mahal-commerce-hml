package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/** Mudança de status do pedido de cliente. */
@Data
public class ProductRequestStatusRequest {

    @NotNull
    private ProductRequestStatus status;

    @Schema(description = "Previsão de chegada (ao encomendar)", nullable = true)
    private LocalDate expectedAt;

    @Size(max = 500)
    @Schema(description = "Fornecedor, link, observação interna", nullable = true)
    private String supplierNote;

    @Size(max = 500)
    @Schema(description = "Obrigatório para CANCELLED", nullable = true)
    private String reason;

    @Schema(description = "ARRIVED sem SKU vinculado: o produto não vai ao catálogo", nullable = true)
    private Boolean withoutSku;
}
