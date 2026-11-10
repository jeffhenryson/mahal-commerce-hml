package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
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

    @DecimalMin("0.0")
    @Schema(description = "V186 — quanto do saldo de cashback do cliente usar nesta venda. Abate do total a pagar "
            + "(os pagamentos somam o total já abatido). Exige customerId com CPF. Sem teto percentual e sem "
            + "saldo mínimo: o limite é o saldo disponível (fora da carência) e o líquido dos itens elegíveis "
            + "(categorias sem `cashbackExcluded`), calculado pelo servidor — acima disso, 422 CASHBACK_ABOVE_CAP. "
            + "Qualquer operador do PDV aplica (não exige PDV_SALE_DISCOUNT). Com `bossDiscountPercent`: 409 "
            + "BENEFIT_CONFLICT. Omitido/0: sem resgate.", example = "20.00")
    private BigDecimal cashbackToRedeem;

    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(5)
    @Schema(description = "Desconto do chefe: percentual inteiro de 1 a 5 (fora disso, 400). O valor continua "
            + "rateado em `items[].discountAmount` e tem que bater com percent × base elegível (líquido das linhas "
            + "de categoria sem `cashbackExcluded`, depois de kit e promoção), ±R$ 0,01 por linha — senão 409 "
            + "DISCOUNT_LIMIT_EXCEEDED. Exige PDV_SALE_DISCOUNT. Não combina com `cashbackToRedeem` (409 "
            + "BENEFIT_CONFLICT), e a venda com ele não gera cashback. Omitido: sem desconto do chefe — e aí "
            + "nenhuma linha pode ter desconto concedido.", example = "5", minimum = "1", maximum = "5")
    private Integer bossDiscountPercent;

    @Valid
    @Schema(description = "Itens avulsos. Pode ser vazio quando a venda tem `kits`; sem itens e sem kits, "
            + "400 SALE_EMPTY.")
    private List<SaleItemRequest> items;

    @Valid
    @jakarta.validation.constraints.Size(max = 20)
    @Schema(description = "Kits montáveis vendidos no balcão (mesmo corpo de POST /pdv/kits/quote). O servidor "
            + "cota cada kit no canal PDV e grava uma linha por escolha, com o desconto do kit já rateado. "
            + "Esse desconto NÃO exige PDV_SALE_DISCOUNT e não conta para o teto de desconto (como na "
            + "comanda), e as unidades do kit ficam fora da promoção por quantidade. Kit que não fecha "
            + "mais: 422 com code KIT_* (KIT_NOT_AVAILABLE, KIT_ITEM_NOT_SELLABLE, KIT_REQUIRED_STEP_MISSING…). "
            + "Não mande as linhas do kit também em `items`. Cada escolha aceita `note` e `discountAmount`; "
            + "este é um desconto concedido, com a permissão e o teto dos itens avulsos.")
    private List<SaleKitSelectionRequest> kits;

    /** Venda vazia: nem item avulso nem kit. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isEmptySale() {
        return (items == null || items.isEmpty()) && (kits == null || kits.isEmpty());
    }

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

    @jakarta.validation.constraints.Pattern(regexp = CLIENT_SALE_ID_PATTERN, message = "clientSaleId deve ser um UUID")
    @Schema(description = "PDV-F043 — chave da venda gerada no caixa (UUID). Opcional: com ela, reenviar a "
            + "mesma venda devolve a já registrada em vez de registrar outra.",
            example = "3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b")
    private String clientSaleId;

    /** UUID em texto, minúsculo ou maiúsculo. */
    public static final String CLIENT_SALE_ID_PATTERN =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @DecimalMin(value = "0.0")
    @Schema(description = "Desconto total de promoção por quantidade que a tela mostrou. O servidor recalcula; "
            + "se divergir (inclusive ausente com promoção aplicável), 409 PROMOTION_CHANGED com "
            + "`currentPromotionDiscount`. Ausente vale 0.")
    private BigDecimal expectedPromotionDiscount;
}
