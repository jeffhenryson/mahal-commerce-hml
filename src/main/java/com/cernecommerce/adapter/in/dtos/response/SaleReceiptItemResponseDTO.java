package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SaleReceiptItemResponseDTO {
    private String sku;
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal netAmount;

    @Schema(description = "Acréscimo manual da linha (PDV-F011); zero sem acréscimo.")
    private BigDecimal surchargeAmount;

    @Schema(description = "Cashback gerado pela linha.")
    private BigDecimal cashbackAmount;

    @Schema(description = "Cortesia da casa (linha a custo zero para o cliente).")
    private boolean courtesy;

    @Schema(description = "Modo de consumo: NORMAL, OPEN_ROSH, SABOR_EXTRA, TROCA.")
    private String mode;

    @Schema(description = "Observação do item (ex.: sabor, ponto).")
    private String notes;

    /** PDV-F024 — carvão da sessão (CUBO/JUMBO); nulo fora de sessão do cardápio. */
    private String carvao;

    @Schema(description = "Regra de promoção por quantidade aplicada na linha (a de maior desconto). Nulo sem promoção.")
    private Long promotionId;

    @Schema(description = "Nome da regra como estava na venda.")
    private String promotionName;

    @Schema(description = "Parte de discountAmount que veio de promoção por quantidade.")
    private java.math.BigDecimal promotionDiscountAmount;
}
