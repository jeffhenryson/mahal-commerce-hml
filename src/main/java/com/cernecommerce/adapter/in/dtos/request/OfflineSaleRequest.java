package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** Uma venda da fila offline do caixa (PDV-F043). */
@Data
public class OfflineSaleRequest {

    @NotBlank
    @Pattern(regexp = SaleRequest.CLIENT_SALE_ID_PATTERN, message = "clientSaleId deve ser um UUID")
    @Schema(description = "Chave da venda gerada no caixa (UUID) — a mesma que iria na venda online.",
            example = "3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientSaleId;

    @NotNull
    @Schema(description = "Quando a venda aconteceu no balcão. Entre a abertura do caixa e agora (+5 min).",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant soldAt;

    private Long customerId;

    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(5)
    @Schema(description = "Desconto do chefe (1–5), como na venda online: o valor rateado em `items[].discountAmount` "
            + "tem que bater com percent × base elegível. Omitido (caixa antigo): o desconto concedido é aceito até "
            + "o teto de 5% sobre a base elegível. Em ambos, desconto numa linha fora dos benefícios recusa a venda "
            + "(DISCOUNT_LIMIT_EXCEEDED).", example = "5", minimum = "1", maximum = "5")
    private Integer bossDiscountPercent;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;

    @NotEmpty
    @Valid
    @Schema(description = "Só DINHEIRO, DEBITO e CREDITO — PIX e marcado precisam de rede.")
    private List<SalePaymentRequest> payments;
}
