package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item de pedido na resposta do PDV.
 *
 * <p>Custo e margem <b>não</b> são expostos aqui, de propósito: são dado de gestão, não de operação
 * de caixa, e {@code PDV_READ} é a permissão mais distribuída do módulo. Eles entram na visão de
 * pedidos do operador e nos relatórios de margem, sob permissão própria.</p>
 */
@Data
public class OrderItemResponseDTO {

    private Long id;
    private String sku;

    @Schema(description = "Nome do produto, congelado no instante da venda. Nulo em pedidos "
            + "anteriores a esta coluna.")
    private String productName;

    private BigDecimal quantity;

    @Schema(description = "Preço praticado, congelado do catálogo no instante da venda.")
    private BigDecimal unitPrice;

    @Schema(description = "Desconto concedido neste item.")
    private BigDecimal discountAmount;

    @Schema(description = "Bruto do item: quantidade x preço unitário.")
    private BigDecimal grossAmount;

    @Schema(description = "Líquido do item: bruto menos desconto. É a base do cashback.")
    private BigDecimal netAmount;

    @Schema(description = "Taxa de cashback vigente na venda. Nula enquanto o programa de "
            + "fidelidade não existir (CRM-F003).")
    private BigDecimal cashbackPercent;

    @Schema(description = "Cashback gerado por este item. Nulo enquanto não houver taxa carimbada.")
    private BigDecimal cashbackAmount;

    @Schema(description = "PDV-F022 — observação da linha, quando houver.", example = "Sem gelo")
    private String note;

    @Schema(description = "Regra de promoção por quantidade aplicada na linha (a de maior desconto). Nulo sem promoção.")
    private Long promotionId;

    @Schema(description = "Nome da regra como estava na venda.")
    private String promotionName;

    @Schema(description = "Parte de discountAmount que veio de promoção por quantidade.")
    private java.math.BigDecimal promotionDiscountAmount;
}
