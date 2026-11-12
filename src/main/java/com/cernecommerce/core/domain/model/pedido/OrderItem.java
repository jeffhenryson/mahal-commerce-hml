package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.ItemDiscountExceedsGrossException;
import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.estoque.Pricing;

import java.math.BigDecimal;

/**
 * Item de um {@link Order} (PDV-F004): o que foi vendido, por quanto, quanto custou e qual taxa de
 * cashback valia no instante da venda.
 *
 * <h2>Por que três valores são congelados aqui</h2>
 * <p>O item guarda cópias, não referências ao catálogo, e cada uma existe por um motivo diferente:</p>
 * <ul>
 *   <li>{@link #unitPrice} — o preço praticado. Sem ele, mudar o preço amanhã reescreveria o
 *       faturamento de ontem.</li>
 *   <li>{@link #costPrice} — <b>o snapshot que mais gente esquece e o mais caro de retrofitar.</b>
 *       Sem ele, a próxima compra que alterar o custo do produto reescreve a margem histórica de
 *       todos os pedidos passados, e não há como reconstruir: o custo antigo não fica em lugar
 *       nenhum. Como o cashback sai da margem, isso não é detalhe contábil — é a base de
 *       calibragem do programa de fidelidade sendo apagada.</li>
 *   <li>{@link #cashbackPercent} — a taxa vigente. Sem ela, mudar a taxa amanhã reescreveria o
 *       valor gerado por pedidos de ontem.</li>
 * </ul>
 *
 * <p>{@link #benefitsExcluded} (V198) — a linha era de categoria fora dos benefícios no instante da
 * venda: não gera cashback, não entra na base do resgate nem na do desconto do chefe.</p>
 *
 * <h2>Desconto é campo próprio, não preço menor</h2>
 * <p>{@link #discountAmount} existe em vez de simplesmente gravar um {@code unitPrice} mais baixo
 * porque é o que permite responder "quanto de margem o desconto do balcão comeu no mês" com um
 * {@code SUM}, em vez de com arqueologia. Um preço menor no request seria indistinguível de erro de
 * digitação, de desconto autorizado e de fraude.</p>
 *
 * <p>Os três campos congelados são anuláveis <b>apenas</b> para reconstituir pedidos anteriores à
 * migração, que nasceram sem eles. Um default zero mentiria sobre a margem; nulo diz a verdade,
 * que é "não se sabe". Item novo passa por {@link #fromCatalog}, que exige preço.</p>
 */
public record OrderItem(
        Long id,
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal costPrice,
        BigDecimal discountAmount,
        BigDecimal cashbackPercent,
        String productName,
        ConsumptionMode mode,
        boolean courtesy,
        String notes,
        BigDecimal surchargeAmount,
        String charcoal,
        Long promotionId,
        String promotionName,
        BigDecimal promotionDiscountAmount,
        boolean benefitsExcluded) {

    public OrderItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        // Dado legado (linha anterior a PDV-F010) lê como NORMAL, não-cortesia.
        mode = mode == null ? ConsumptionMode.NORMAL : mode;
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (unitPrice != null && unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice não pode ser negativo");
        }
        if (costPrice != null && costPrice.signum() < 0) {
            throw new IllegalArgumentException("costPrice não pode ser negativo");
        }
        discountAmount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (discountAmount.signum() < 0) {
            throw new IllegalArgumentException("discountAmount não pode ser negativo");
        }
        if (unitPrice != null && discountAmount.compareTo(quantity.multiply(unitPrice)) > 0) {
            throw new IllegalArgumentException(
                    "discountAmount não pode ser maior que o bruto do item: desconto que zera o item é devolução, não venda");
        }
        if (cashbackPercent != null
                && (cashbackPercent.signum() < 0 || cashbackPercent.compareTo(Money.HUNDRED) > 0)) {
            throw new IllegalArgumentException("cashbackPercent deve estar entre 0 e 100");
        }
        // PDV-F011 — o acréscimo já está dentro de unitPrice; aqui ele é o registro da parcela.
        // Negativo seria um desconto entrando sem passar por discountAmount nem pelo teto.
        if (surchargeAmount != null && surchargeAmount.signum() < 0) {
            throw new IllegalArgumentException("surchargeAmount não pode ser negativo");
        }
        // Promoção por quantidade: é PARTE de discountAmount (que continua sendo o total da linha),
        // separada só para o relatório distinguir regra da loja de desconto manual/kit.
        promotionDiscountAmount = promotionDiscountAmount == null ? BigDecimal.ZERO : promotionDiscountAmount;
        if (promotionDiscountAmount.signum() < 0) {
            throw new IllegalArgumentException("promotionDiscountAmount não pode ser negativo");
        }
        if (promotionDiscountAmount.compareTo(discountAmount) > 0) {
            throw new IllegalArgumentException("promotionDiscountAmount não pode ser maior que discountAmount");
        }
        if (promotionDiscountAmount.signum() == 0) {
            promotionId = null;
            promotionName = null;
        }
    }

    /**
     * Monta um item novo com o preço e o custo vindos do <b>catálogo</b>, nunca do chamador
     * (PDV-F004). É o único caminho para item de pedido novo, e é isso que fecha o furo de quem
     * tem {@code PDV_SALE_MANAGE} vender qualquer coisa por qualquer preço.
     *
     * <p>A taxa de cashback fica nula aqui: ela é resolvida pela cadeia de escopos do programa de
     * fidelidade, não pelo catálogo, e é carimbada depois via {@link #withCashbackPercent}.</p>
     *
     * @param pricing precificação vigente, tipicamente de {@code EstoqueUseCase.findPricingBySku}
     * @throws ProductNotPricedException se o produto não tem preço a cobrar. Preço zero e preço
     *         desconhecido não são a mesma coisa: um default zero faria o PDV vender de graça em
     *         vez de recusar a venda de item sem preço.
     * @deprecated sem {@code productName} (dado legado lê como {@code null}, e o operador vê só o
     *             SKU no histórico). Prefira a sobrecarga de 5 argumentos.
     */
    @Deprecated
    public static OrderItem fromCatalog(String sku, BigDecimal quantity, Pricing pricing,
            BigDecimal discountAmount) {
        return fromCatalog(sku, quantity, pricing, discountAmount, null);
    }

    /**
     * Igual a {@link #fromCatalog(String, BigDecimal, Pricing, BigDecimal)}, também congelando o
     * nome do produto no instante da venda — mesma razão de {@link #costPrice}: se o produto for
     * renomeado depois, o histórico não pode mudar junto.
     */
    public static OrderItem fromCatalog(String sku, BigDecimal quantity, Pricing pricing,
            BigDecimal discountAmount, String productName) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        // PDV-C016 — a mesma regra do compact constructor, recusada aqui com exceção TIPADA.
        // Este é o caminho de entrada: o discountAmount vem do cliente HTTP, e a invariante do
        // record sobe como IllegalArgumentException, que o handler global achata num 400 genérico
        // (BAD_REQUEST, "Requisição inválida") descartando a mensagem — a mesma resposta de
        // qualquer corpo malformado. A checagem do record continua onde está: ela é a rede contra
        // erro de programação e contra dado corrompido, não a validação da borda.
        if (discountAmount != null && quantity != null && discountAmount.signum() > 0) {
            BigDecimal gross = quantity.multiply(pricing.effectivePrice());
            if (discountAmount.compareTo(gross) > 0) {
                throw new ItemDiscountExceedsGrossException(sku, discountAmount, gross);
            }
        }
        return new OrderItem(null, sku, quantity, pricing.effectivePrice(), pricing.costPrice(),
                discountAmount, null, productName, ConsumptionMode.NORMAL, false, null, null, null, null, null, null,
                false);
    }

    /**
     * Reconstitui um item a partir de persistência.
     * @deprecated sem {@code productName}. Prefira a sobrecarga de 8 argumentos.
     */
    @Deprecated
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent) {
        return of(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, null);
    }

    /** Reconstitui um item a partir de persistência, com o nome do produto congelado (dado legado: {@code null}). */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent, String productName) {
        return of(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName,
                ConsumptionMode.NORMAL, false);
    }

    /**
     * Reconstitui um item a partir de persistência, com o modo da sessão de mesa (PDV-F010).
     *
     * <p>{@code mode}/{@code courtesy} viajam do {@code ComandaItem} para cá no fechamento da
     * comanda. Sem eles no pedido, o histórico da mesa não distingue cortesia de item cobrado — e
     * inferir por preço zero mentiria, porque um desconto de 100% dá o mesmo zero.</p>
     */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent, String productName,
            ConsumptionMode mode, boolean courtesy) {
        return of(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName, mode,
                courtesy, null, null);
    }

    /**
     * Reconstitui um item carregando também o setup da mesa e o acréscimo do open rosh (PDV-F011).
     *
     * <p>Os dois atravessam o fechamento da comanda pela mesma razão que obrigou {@code mode} e
     * {@code courtesy} a atravessarem: a pergunta <i>"qual pinça saiu com aquela mesa"</i> é feita
     * <b>depois</b> de a mesa ter fechado, e a parcela de acréscimo não pode ser reconstruída a
     * partir do {@code unitPrice} — ele já é a soma. Nulos em toda venda que não veio de mesa.</p>
     */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent, String productName,
            ConsumptionMode mode, boolean courtesy, String notes, BigDecimal surchargeAmount) {
        return of(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName, mode,
                courtesy, notes, surchargeAmount, null);
    }

    /**
     * Reconstitui um item com o carvão da sessão (PDV-F024) — só registro, sem efeito em preço. Nulo
     * em toda linha que não é sessão do cardápio.
     */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent, String productName,
            ConsumptionMode mode, boolean courtesy, String notes, BigDecimal surchargeAmount, String charcoal) {
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName,
                mode, courtesy, notes, surchargeAmount, charcoal, null, null, null, false);
    }

    /** Reconstitui um item com o detalhamento da promoção por quantidade (zero/nulos sem promoção). */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent, String productName,
            ConsumptionMode mode, boolean courtesy, String notes, BigDecimal surchargeAmount, String charcoal,
            Long promotionId, String promotionName, BigDecimal promotionDiscountAmount) {
        return of(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName, mode,
                courtesy, notes, surchargeAmount, charcoal, promotionId, promotionName, promotionDiscountAmount, false);
    }

    /** Reconstitui um item com a marcação de fora dos benefícios (V198). */
    public static OrderItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, BigDecimal discountAmount, BigDecimal cashbackPercent, String productName,
            ConsumptionMode mode, boolean courtesy, String notes, BigDecimal surchargeAmount, String charcoal,
            Long promotionId, String promotionName, BigDecimal promotionDiscountAmount, boolean benefitsExcluded) {
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName,
                mode, courtesy, notes, surchargeAmount, charcoal, promotionId, promotionName, promotionDiscountAmount,
                benefitsExcluded);
    }

    /**
     * Aplica o desconto de promoção por quantidade calculado pelo servidor: <b>soma</b> ao
     * {@code discountAmount} da linha e guarda o detalhamento. Desconto zero devolve a própria linha.
     */
    public OrderItem withPromotion(Long newPromotionId, String newPromotionName, BigDecimal amount) {
        if (amount == null || amount.signum() == 0) {
            return this;
        }
        BigDecimal promo = Money.of(amount);
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount.add(promo), cashbackPercent,
                productName, mode, courtesy, notes, surchargeAmount, charcoal, newPromotionId, newPromotionName,
                promotionDiscountAmount.add(promo), benefitsExcluded);
    }

    /** Carimba a taxa de cashback vigente. Cópia — o item permanece imutável. */
    public OrderItem withCashbackPercent(BigDecimal newCashbackPercent) {
        // V198 — linha fora dos benefícios fica em zero, qualquer que seja a taxa resolvida.
        BigDecimal percent = benefitsExcluded ? BigDecimal.ZERO : newCashbackPercent;
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, percent, productName,
                mode, courtesy, notes, surchargeAmount, charcoal, promotionId, promotionName, promotionDiscountAmount,
                benefitsExcluded);
    }

    /**
     * V198 — marca a linha como de categoria fora dos benefícios (cigarros, palheiros…). Excluída, a
     * taxa de cashback é zerada junto: nem uma taxa por SKU nem a global geram cashback sobre ela.
     */
    public OrderItem withBenefitsExcluded(boolean excluded) {
        BigDecimal percent = excluded ? BigDecimal.ZERO : cashbackPercent;
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, percent, productName,
                mode, courtesy, notes, surchargeAmount, charcoal, promotionId, promotionName, promotionDiscountAmount,
                excluded);
    }

    /**
     * Observação livre da linha (PDV-F022) — "sem gelo", "embrulhar para presente". Mesma coluna
     * {@code notes} que a mesa já usava; o limite de tamanho é validado na borda.
     */
    public OrderItem withNotes(String newNotes) {
        String trimmed = newNotes == null || newNotes.isBlank() ? null : newNotes.trim();
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, discountAmount, cashbackPercent, productName,
                mode, courtesy, trimmed, surchargeAmount, charcoal, promotionId, promotionName, promotionDiscountAmount,
                benefitsExcluded);
    }

    /** Concede desconto neste item. Cópia — o item permanece imutável. */
    public OrderItem withDiscount(BigDecimal newDiscountAmount) {
        return new OrderItem(id, sku, quantity, unitPrice, costPrice, newDiscountAmount, cashbackPercent, productName,
                mode, courtesy, notes, surchargeAmount, charcoal, promotionId, promotionName, promotionDiscountAmount,
                benefitsExcluded);
    }

    /**
     * Bruto do item: {@code quantity * unitPrice}.
     *
     * @return {@code null} para item legado sem preço congelado.
     */
    public BigDecimal grossAmount() {
        return unitPrice == null ? null : quantity.multiply(unitPrice).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Líquido do item: bruto menos desconto. É a base do cashback e da margem.
     *
     * @return {@code null} para item legado sem preço congelado.
     */
    public BigDecimal netAmount() {
        BigDecimal gross = grossAmount();
        return gross == null ? null : gross.subtract(discountAmount);
    }

    /**
     * Cashback gerado: {@code netAmount * cashbackPercent / 100}. Sobre o <b>líquido</b>, com a
     * taxa <b>do próprio item</b> — uma taxa média sobre o total do pedido impediria qualquer
     * análise por item, que é justamente onde o programa se paga ou se perde.
     *
     * @return {@code null} se falta preço ou taxa carimbada.
     */
    public BigDecimal cashbackAmount() {
        BigDecimal net = netAmount();
        if (net == null || cashbackPercent == null) {
            return null;
        }
        return net.multiply(cashbackPercent)
                .divide(Money.HUNDRED, Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Lucro bruto do item: {@code netAmount - costPrice * quantity}. Negativo quando se vendeu
     * abaixo do custo, o que continua permitido — queima de estoque e produto-isca são decisões
     * comerciais legítimas, na mesma política de {@code Pricing.isBelowCost()}.
     *
     * @return {@code null} se falta preço ou custo congelado.
     */
    public BigDecimal marginAmount() {
        BigDecimal net = netAmount();
        if (net == null || costPrice == null) {
            return null;
        }
        return net.subtract(quantity.multiply(costPrice)).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }
}
