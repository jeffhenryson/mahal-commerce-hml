package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * Venda de balcão.
 *
 * <p><b>{@code warehouseCode} saiu deste request em PDV-C004.</b> O depósito passa a vir da sessão
 * de caixa, o que impede o operador de baixar estoque de um depósito que não é o do caixa dele.</p>
 */
@Data
public class SaleRequest {

    @Schema(description = "Cliente identificado, opcional. Sem cliente não há cashback — é esse o "
            + "incentivo que faz o operador perguntar \"CPF na nota?\".", example = "42")
    private Long customerId;

    @NotEmpty
    @Valid
    private List<SaleItemRequest> items;

    @NotEmpty
    @Valid
    @Schema(description = "Pelo menos uma linha (PDV-F006). Várias linhas = pagamento dividido.")
    private List<SalePaymentRequest> payments;

    @Schema(description = "PDV-F008 — reservar para retirada depois em vez de concluir na hora. "
            + "Omitido/false: comportamento de sempre (CONCLUIDO). true: mercadoria já baixada e "
            + "pagamento já capturado, mas o pedido grava RESERVADO até o cliente voltar para "
            + "retirar.")
    private Boolean reserveForPickup;

    @Valid
    @Schema(description = "PDV-F022 — entrega ou retirada. ENTREGA grava RESERVADO e segue a esteira; "
            + "RETIRADA grava CONCLUIDO (retirada imediata) ou RESERVADO com reserveForPickup=true. "
            + "delivery.fee entra no total a pagar.")
    private DeliveryRequest delivery;
}
