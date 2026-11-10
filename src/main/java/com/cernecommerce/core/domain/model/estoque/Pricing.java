package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;

/**
 * Precificação de um {@link Product} (EST-F019): custo de aquisição, markup desejado e preço
 * praticado. Value object sem identidade própria — persistido em colunas de {@code product}.
 *
 * <p>Os três campos são opcionais e independentes: o catálogo aceita produto sem preço nenhum
 * (era o único estado possível antes desta feature) e produto com custo mas sem preço de venda
 * definido. {@link #empty()} representa "não precificado".</p>
 *
 * <h2>Markup não é margem</h2>
 * <p>São duas divisões diferentes sobre a mesma diferença, e confundi-las é o erro clássico de
 * precificação de varejo:</p>
 * <ul>
 *   <li><b>Markup</b> ({@link #markupPercent}) é sobre o <b>custo</b> — quanto se acrescenta ao
 *       que se pagou. É o <i>input</i> do lojista: "compro a 50 e quero 100% em cima".</li>
 *   <li><b>Margem</b> ({@link #marginPercent()}) é sobre a <b>venda</b> — que fatia do que
 *       entrou no caixa sobrou. É o que interessa ao resultado.</li>
 * </ul>
 * <p>Custo 50 e venda 100 são markup de <b>100%</b> e margem de <b>50%</b>. Por isso ambos são
 * expostos: o markup para precificar, a margem para saber quanto se ganhou de fato — e para
 * dimensionar cashback, que sai da margem, não do faturamento.</p>
 *
 * <h2>Preço sugerido x preço praticado</h2>
 * <p>{@link #suggestedPrice()} é o que o markup manda cobrar. {@link #salePrice} é o que a loja
 * cobra de verdade, e <b>vence</b> quando informado — é o espaço para preço psicológico
 * (R$ 49,90 em vez dos R$ 48,73 que a fórmula devolveu) e para preço de tabela do fornecedor.
 * {@link #effectivePrice()} resolve os dois: o praticado se existir, senão o sugerido.</p>
 *
 * <p>Quando o preço praticado diverge do sugerido, {@link #effectiveMarkupPercent()} devolve o
 * markup que o preço praticado <i>realmente</i> representa — o número honesto, em oposição ao
 * markup pretendido que ficou guardado em {@link #markupPercent}.</p>
 *
 * <h2>Preço "de/por"</h2>
 * <p>{@link #originalPrice} é puramente um valor de exibição — o preço "riscado" para dar
 * sensação de desconto na vitrine. Não tem nenhuma relação obrigatória com {@link #salePrice}
 * ou {@link #effectivePrice()}: o domínio não valida que ele seja maior que o preço praticado.
 * {@link #hasDiscount()} é quem decide, na leitura, se o desconto exibido faz sentido.</p>
 *
 * <h2>Preço extraordinário</h2>
 * <p>{@link #causeAmount} é um valor adicional opcional, por fora do preço de venda normal,
 * destinado a uma causa. Puramente informativo: não entra em {@link #effectivePrice()} nem em
 * nenhum outro cálculo derivado — se cobrar isso do cliente ou não é decisão de produto ainda em
 * aberto, então o domínio hoje só registra o valor, sem efeito colateral.</p>
 */
public record Pricing(BigDecimal costPrice, BigDecimal markupPercent, BigDecimal salePrice,
        BigDecimal originalPrice, BigDecimal causeAmount) {

    private static final Pricing EMPTY = new Pricing(null, null, null, null, null);

    public Pricing {
        if (costPrice != null && costPrice.signum() < 0) {
            throw new IllegalArgumentException("costPrice não pode ser negativo");
        }
        if (markupPercent != null && markupPercent.signum() < 0) {
            throw new IllegalArgumentException("markupPercent não pode ser negativo");
        }
        if (salePrice != null && salePrice.signum() < 0) {
            throw new IllegalArgumentException("salePrice não pode ser negativo");
        }
        if (originalPrice != null && originalPrice.signum() < 0) {
            throw new IllegalArgumentException("originalPrice não pode ser negativo");
        }
        if (causeAmount != null && causeAmount.signum() < 0) {
            throw new IllegalArgumentException("causeAmount não pode ser negativo");
        }
    }

    /**
     * Troca só o custo, inclusive para nulo — diferente de {@code withPatch}, em que nulo mantém.
     * Usado pelo custo derivado do kit pré-montado (soma dos itens), que pode virar "desconhecido".
     */
    public Pricing withCostPrice(BigDecimal newCostPrice) {
        return new Pricing(newCostPrice, markupPercent, salePrice, originalPrice, causeAmount);
    }

    /** Produto sem precificação — nenhum dos campos definido. */
    public static Pricing empty() {
        return EMPTY;
    }

    /** Precificação a partir dos cinco campos; qualquer um pode ser nulo. */
    public static Pricing of(BigDecimal costPrice, BigDecimal markupPercent, BigDecimal salePrice,
            BigDecimal originalPrice, BigDecimal causeAmount) {
        return new Pricing(costPrice, markupPercent, salePrice, originalPrice, causeAmount);
    }

    /** Precificação a partir de quatro campos; {@code causeAmount} fica nulo. */
    public static Pricing of(BigDecimal costPrice, BigDecimal markupPercent, BigDecimal salePrice,
            BigDecimal originalPrice) {
        return new Pricing(costPrice, markupPercent, salePrice, originalPrice, null);
    }

    /** Precificação a partir dos três campos originais; {@code originalPrice}/{@code causeAmount} ficam nulos. */
    public static Pricing of(BigDecimal costPrice, BigDecimal markupPercent, BigDecimal salePrice) {
        return new Pricing(costPrice, markupPercent, salePrice, null, null);
    }

    /** Precificação por markup: o preço de venda fica a cargo de {@link #suggestedPrice()}. */
    public static Pricing byMarkup(BigDecimal costPrice, BigDecimal markupPercent) {
        return new Pricing(costPrice, markupPercent, null, null, null);
    }

    /**
     * Preço que o markup manda cobrar: {@code costPrice * (1 + markupPercent / 100)}, arredondado
     * a 2 casas com {@code HALF_UP}.
     *
     * @return {@code null} se custo ou markup não estiverem definidos — sem os dois não há o que
     *         sugerir.
     */
    public BigDecimal suggestedPrice() {
        if (costPrice == null || markupPercent == null) {
            return null;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(
                markupPercent.divide(Money.HUNDRED, Money.INTERMEDIATE_SCALE, Money.ROUNDING));
        return costPrice.multiply(multiplier).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Preço a ser cobrado de fato: o praticado ({@link #salePrice}) quando informado, senão o
     * {@link #suggestedPrice()}. É o valor que o PDV e a vitrine devem consumir.
     *
     * @return {@code null} quando o produto não tem preço praticado nem como sugerir um.
     */
    public BigDecimal effectivePrice() {
        return salePrice != null ? salePrice : suggestedPrice();
    }

    /** Indica se há preço a cobrar — praticado ou sugerido. */
    public boolean isPriced() {
        return effectivePrice() != null;
    }

    /**
     * Indica que <b>nenhum</b> dos cinco campos foi preenchido.
     *
     * <p>Distinto de {@code !isPriced()}: uma precificação só com {@code costPrice} não tem preço
     * a cobrar, mas carrega informação. A diferença importa em EST-F020, onde "a variação
     * declarou algum preço próprio" e "a variação tem preço de venda próprio" são perguntas
     * diferentes — usar {@code isPriced()} ali faria um custo próprio da variação ser ignorado.</p>
     */
    public boolean isEmpty() {
        return costPrice == null && markupPercent == null && salePrice == null && originalPrice == null
                && causeAmount == null;
    }

    /**
     * Lucro bruto por unidade: {@code effectivePrice - costPrice}. Negativo quando se vende
     * abaixo do custo (ver {@link #isBelowCost()}).
     *
     * @return {@code null} se faltar custo ou preço efetivo.
     */
    public BigDecimal marginAmount() {
        BigDecimal price = effectivePrice();
        if (costPrice == null || price == null) {
            return null;
        }
        return price.subtract(costPrice).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }

    /**
     * Margem sobre a <b>venda</b>: {@code (effectivePrice - costPrice) / effectivePrice * 100}.
     * Nunca passa de 100%. É a fatia do faturamento que sobra — a base sobre a qual se
     * dimensiona desconto e cashback.
     *
     * @return {@code null} se faltar custo, faltar preço efetivo, ou o preço efetivo for zero
     *         (divisão indefinida — brinde não tem margem percentual).
     */
    public BigDecimal marginPercent() {
        BigDecimal price = effectivePrice();
        if (costPrice == null || price == null || price.signum() == 0) {
            return null;
        }
        return price.subtract(costPrice)
                .divide(price, Money.INTERMEDIATE_SCALE, Money.ROUNDING)
                .multiply(Money.HUNDRED)
                .setScale(Money.PERCENT_SCALE, Money.ROUNDING);
    }

    /**
     * Markup que o preço efetivo <b>realmente</b> representa:
     * {@code (effectivePrice - costPrice) / costPrice * 100}. Difere de {@link #markupPercent}
     * sempre que há preço praticado divergente do sugerido — é o número a exibir para o lojista
     * conferir se o arredondamento comercial comeu a margem pretendida.
     *
     * @return {@code null} se faltar custo, faltar preço efetivo, ou o custo for zero (divisão
     *         indefinida — sobre custo zero todo markup é infinito).
     */
    public BigDecimal effectiveMarkupPercent() {
        BigDecimal price = effectivePrice();
        if (costPrice == null || price == null || costPrice.signum() == 0) {
            return null;
        }
        return price.subtract(costPrice)
                .divide(costPrice, Money.INTERMEDIATE_SCALE, Money.ROUNDING)
                .multiply(Money.HUNDRED)
                .setScale(Money.PERCENT_SCALE, Money.ROUNDING);
    }

    /**
     * Indica se o preço efetivo está <b>abaixo</b> do custo — prejuízo por unidade vendida.
     * Não é bloqueado pelo domínio (queima de estoque e produto-isca são decisões comerciais
     * legítimas), mas é sinalizado para a UI avisar.
     */
    public boolean isBelowCost() {
        BigDecimal margin = marginAmount();
        return margin != null && margin.signum() < 0;
    }

    /**
     * Indica se há desconto "de/por" a exibir: {@link #originalPrice} definido e
     * <b>estritamente maior</b> que {@link #effectivePrice()}. A checagem evita que o selo de
     * desconto minta quando o admin digitar um {@code originalPrice} igual ou menor que o preço
     * praticado — nesse caso não há desconto real, então não deve ser exibido.
     */
    public boolean hasDiscount() {
        BigDecimal price = effectivePrice();
        return originalPrice != null && price != null && originalPrice.compareTo(price) > 0;
    }

    /**
     * Percentual de desconto do preço "de/por": {@code (originalPrice - effectivePrice) /
     * originalPrice * 100}.
     *
     * @return {@code null} quando {@link #hasDiscount()} é falso — sem desconto real, não há
     *         percentual a exibir.
     */
    public BigDecimal discountPercent() {
        if (!hasDiscount()) {
            return null;
        }
        BigDecimal price = effectivePrice();
        return originalPrice.subtract(price)
                .divide(originalPrice, Money.INTERMEDIATE_SCALE, Money.ROUNDING)
                .multiply(Money.HUNDRED)
                .setScale(Money.PERCENT_SCALE, Money.ROUNDING);
    }

    /**
     * Alteração parcial (mesma semântica de {@code Product.withDetails}): argumento nulo
     * significa <b>não mexer neste campo</b>.
     *
     * <p>Consequência conhecida, idêntica à de {@code Product.withDetails}: como {@code null}
     * quer dizer "manter", não há como <b>limpar</b> um campo já preenchido por este caminho —
     * o máximo é trocá-lo. Despreficicar exige recriar via {@link #of}.</p>
     */
    public Pricing withPatch(BigDecimal newCostPrice, BigDecimal newMarkupPercent, BigDecimal newSalePrice,
            BigDecimal newOriginalPrice, BigDecimal newCauseAmount) {
        return new Pricing(
                newCostPrice == null ? costPrice : newCostPrice,
                newMarkupPercent == null ? markupPercent : newMarkupPercent,
                newSalePrice == null ? salePrice : newSalePrice,
                newOriginalPrice == null ? originalPrice : newOriginalPrice,
                newCauseAmount == null ? causeAmount : newCauseAmount);
    }

    /** Mesmo que a forma de 5 argumentos, mas sem tocar em {@code causeAmount}. */
    public Pricing withPatch(BigDecimal newCostPrice, BigDecimal newMarkupPercent, BigDecimal newSalePrice,
            BigDecimal newOriginalPrice) {
        return withPatch(newCostPrice, newMarkupPercent, newSalePrice, newOriginalPrice, null);
    }

    /** Mesmo que a forma de 5 argumentos, mas sem tocar em {@code originalPrice}/{@code causeAmount}. */
    public Pricing withPatch(BigDecimal newCostPrice, BigDecimal newMarkupPercent, BigDecimal newSalePrice) {
        return withPatch(newCostPrice, newMarkupPercent, newSalePrice, null, null);
    }

    /**
     * Fixa o preço sugerido como preço praticado, congelando o resultado do markup atual.
     * Usado quando o lojista aceita a sugestão sem edição — a partir daí, mexer no custo não
     * move mais o preço sozinho.
     *
     * @return {@code this} se não há sugestão a materializar.
     */
    public Pricing materializeSuggestion() {
        BigDecimal suggested = suggestedPrice();
        return suggested == null ? this : new Pricing(costPrice, markupPercent, suggested, originalPrice, causeAmount);
    }
}
