package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fechamento de comanda. Mesmo shape de pagamento de {@code SaleRequest} (PDV-F006): pelo menos
 * uma linha, várias linhas = pagamento dividido.
 */
@Data
public class CloseComandaRequest {

    @NotEmpty
    @Valid
    @Schema(description = "Pelo menos uma linha. Várias linhas = pagamento dividido.")
    private List<SalePaymentRequest> payments;

    @DecimalMin(value = "0.0")
    @Schema(description = "PDV-F014 — valor em R$ do desconto do chefe sobre a CONTA INTEIRA. Tem que "
            + "bater com bossDiscountPercent × base elegível (líquido das linhas em aberto com "
            + "cashbackExcluded=false), ±R$ 0,01 por linha — senão 409 DISCOUNT_LIMIT_EXCEEDED; sem "
            + "bossDiscountPercent, também 409. O servidor rateia só entre as linhas elegíveis, "
            + "proporcionalmente ao valor de cada uma. Exige PDV_COMANDA_DISCOUNT. "
            + "Não confundir com surchargeAmount, que é acréscimo por linha no open rosh.",
            example = "20.00")
    private BigDecimal discountAmount;

    @jakarta.validation.constraints.Min(1)
    @jakarta.validation.constraints.Max(5)
    @Schema(description = "Desconto do chefe: percentual inteiro de 1 a 5 (fora disso, 400), com o valor em "
            + "discountAmount. Só no fechamento da conta inteira (com itemIds: 409 DISCOUNT_LIMIT_EXCEEDED). "
            + "Exige PDV_COMANDA_DISCOUNT. Não combina com cashbackToRedeem (409 BENEFIT_CONFLICT), e o "
            + "fechamento com ele não gera cashback.", example = "5", minimum = "1", maximum = "5")
    private Integer bossDiscountPercent;

    @Schema(description = "PDV-F015 — taxa de serviço (os 10% do garçom). Vem APLICADA POR PADRÃO, "
            + "porque é o padrão do salão: mandar false é o cliente recusando. O valor é calculado "
            + "pelo servidor como percentual sobre o líquido (portanto depois do desconto) e "
            + "gravado em campo próprio, fora do netAmount — o líquido é receita da casa, a taxa é "
            + "repasse. Consulte o percentual vigente em GET /pdv/comandas/service-fee.",
            defaultValue = "true")
    private Boolean applyServiceFee;

    @Schema(description = "PDV-F017 — conta dividida: ids das linhas que ESTE fechamento cobra. "
            + "Omitido ou vazio cobra tudo que está em aberto (o comportamento de sempre). Com a "
            + "lista preenchida, o pedido sai só com essas linhas, elas são marcadas como cobradas "
            + "e a comanda CONTINUA ABERTA com o restante — repita até não sobrar nada, e o último "
            + "fechamento encerra a mesa. Desconto, taxa e troco incidem só sobre o escopo. Um "
            + "OPEN_ROSH e as TROCA/SABOR_EXTRA ligados a ele têm que sair juntos: separar devolve "
            + "409 LINKED_ITEM_MUST_CLOSE_TOGETHER.",
            example = "[3, 5, 7]")
    private List<Long> itemIds;

    @jakarta.validation.constraints.DecimalMin("0.0")
    @Schema(description = "V186 — quanto do saldo de cashback do cliente da comanda usar neste fechamento. Abate "
            + "do total a pagar, depois da taxa de serviço (a taxa não encolhe). Sem teto percentual e sem saldo "
            + "mínimo: o limite é o saldo disponível e o líquido das linhas elegíveis deste fechamento "
            + "(cashbackExcluded=false; conta dividida: cada fechamento tem o seu) — acima, 422 "
            + "CASHBACK_ABOVE_CAP. Exige comanda com cliente com CPF; qualquer operador aplica, sem permissão "
            + "de desconto. Com bossDiscountPercent: 409 BENEFIT_CONFLICT. Omitido/0: sem resgate.",
            example = "15.00")
    private BigDecimal cashbackToRedeem;

    /** Ausente é "sim": a taxa é o padrão do salão, e omitir não pode significar deixar de cobrar. */
    public boolean isServiceFeeApplied() {
        return applyServiceFee == null || applyServiceFee;
    }
}
