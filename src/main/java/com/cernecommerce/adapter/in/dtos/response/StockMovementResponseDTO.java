package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha do ledger de movimentações de estoque. {@code warehouseCode} é o código informado na
 * consulta — o domínio guarda apenas o {@code warehouseId}.
 */
@Data
public class StockMovementResponseDTO {
    private Long id;
    private String sku;
    private String warehouseCode;
    private String type;
    private BigDecimal quantity;
    private String reason;
    private String username;
    private Instant createdAt;
    private String lotCode;
    /** Custo unitário da entrada (EST-F007). Nulo em SAIDA/AJUSTE ou entrada sem custo conhecido. */
    private BigDecimal unitCost;
    /** Unidade de medida do produto dono da movimentação. Nula quando o SKU não resolve mais a um produto. */
    private String unit;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Origem da movimentação: ORDER, COMANDA, GOODS_RECEIPT, "
            + "RESERVATION, TRANSFER, PACKAGING ou MANUAL. Nula no histórico anterior a 02/11/2026 quando não dava "
            + "para deduzir.", nullable = true)
    private String originType;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Id do documento de origem: pedido (ORDER), comanda "
            + "(COMANDA), recebimento (GOODS_RECEIPT) ou reserva (RESERVATION). Nulo nos demais tipos.", nullable = true)
    private Long originId;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Vínculo com o recebimento de mercadoria (ENTRADA de compra).",
            nullable = true)
    private Long goodsReceiptId;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Saldo físico do SKU no depósito antes da movimentação. "
            + "Nulo no histórico anterior a 02/11/2026.", nullable = true)
    private BigDecimal balanceBefore;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Saldo físico depois da movimentação (em AJUSTE, o "
            + "contado). Nulo no histórico anterior a 02/11/2026.", nullable = true)
    private BigDecimal balanceAfter;
}
