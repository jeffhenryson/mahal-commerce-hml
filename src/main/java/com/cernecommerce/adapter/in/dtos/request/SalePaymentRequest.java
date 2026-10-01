package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Uma linha de pagamento de uma venda de balcão (PDV-F006). Várias linhas = pagamento dividido
 * (ex.: R$50 em dinheiro + R$30 no débito).
 */
@Data
public class SalePaymentRequest {

    @NotBlank
    @Schema(description = "DINHEIRO, DEBITO, CREDITO, PIX ou MARCADO (CRM-F010: venda a prazo para "
            + "cliente VIP; no máximo uma linha, com dueDate, e exige PDV_SALE_ON_ACCOUNT).", example = "DINHEIRO")
    private String method;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(description = "Em DINHEIRO, é o quanto o cliente entregou — pode passar do total da "
            + "venda e virar troco. Nos demais métodos, é o valor exato cobrado: não pode, sozinho "
            + "ou somado a outras linhas não-DINHEIRO, passar do líquido do pedido.", example = "50.00")
    private BigDecimal amount;

    @Min(1)
    @Max(24)
    @Schema(description = "Só em CREDITO. Ausente nos demais métodos.", example = "3")
    private Integer installments;

    @Schema(description = "Por onde a cobrança não-dinheiro saiu: MAQUININHA ou LINK (PDV-F025). Proibido "
            + "em DINHEIRO (400 INVALID_PAYMENT_CHANNEL).", example = "MAQUININHA")
    private PaymentChannel channel;

    @Schema(description = "Operadora: CIELO ou INFINITYPAY (PDV-F025). Exige channel.", example = "CIELO")
    private PaymentProvider provider;

    @Schema(description = "CRM-F010 — vencimento da linha MARCADO (AAAA-MM-DD, hoje ou depois). "
            + "Obrigatório em MARCADO; ignorado nos demais métodos.", example = "2026-10-15")
    private java.time.LocalDate dueDate;
}
