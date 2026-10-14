package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Item de pedido na visão do administrador.
 *
 * <p>Difere de {@link OrderItemResponseDTO} por expor <b>custo e margem</b>. Não é duplicação
 * gratuita: {@code PDV_READ} é a permissão mais distribuída do módulo, e o operador de caixa não
 * precisa — nem deve — ver quanto a loja ganha em cada item.</p>
 */
@Data
public class OrderItemAdminResponseDTO {

    private Long id;
    private String sku;

    @Schema(description = "Nome do produto, congelado no instante da venda — não muda se o produto "
            + "for renomeado depois. Nulo em pedidos anteriores a esta coluna.")
    private String productName;

    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal discountAmount;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;

    @Schema(description = "Custo unitário congelado na venda. Nulo em pedidos anteriores à V65 — "
            + "um zero aqui mentiria sobre a margem.")
    private BigDecimal costPrice;

    @Schema(description = "Lucro bruto do item: líquido − custo × quantidade. Negativo quando se "
            + "vendeu abaixo do custo, o que é permitido e apenas sinalizado.")
    private BigDecimal marginAmount;

    private BigDecimal cashbackPercent;
    private BigDecimal cashbackAmount;

    @Schema(description = "Modo da linha na comanda que originou o pedido: NORMAL, OPEN_ROSH, "
            + "SABOR_EXTRA ou TROCA (PDV-F010). NORMAL em toda venda que não veio de mesa.")
    // PDV-C010 — ver ComandaItemResponseDTO.mode.
    private ConsumptionMode mode;

    @Schema(description = "Linha cortesia: cobrada a zero, com o custo congelado normalmente. É o "
            + "que faz a margem mostrar o prejuízo real da promo e do open rosh. Campo próprio, "
            + "não inferido de netAmount = 0 — um desconto de 100% dá o mesmo zero.")
    private boolean courtesy;

    @Schema(description = "Setup registrado na linha da comanda que originou o pedido — qual "
            + "narguilé, qual pinça (PDV-F011). Herdado no fechamento porque a pergunta 'qual "
            + "pinça saiu com aquela mesa' é feita depois de a mesa ter fechado. Nulo em toda "
            + "venda que não veio de mesa.")
    private String notes;

    @Schema(description = "Carvão da sessão de narguilé (CUBO ou JUMBO), só registro (PDV-F024). Nulo "
            + "fora de sessão do cardápio.", example = "JUMBO")
    private String carvao;

    @Schema(description = "Parcela de unitPrice que veio de acréscimo manual no open rosh "
            + "(PDV-F011). Não é reconstruível a partir de unitPrice, que já é a soma — daí o "
            + "campo próprio, na mesma lógica de discountAmount. Nulo em toda venda que não veio "
            + "de mesa.")
    private BigDecimal surchargeAmount;

    @Schema(description = "PED-F004 — unidades já estornadas desta linha. Só no GET /orders/{id} e na resposta do "
            + "estorno; nulo nas listagens. O próximo estorno aceita até quantity − refundedQuantity.")
    private BigDecimal refundedQuantity;
}
