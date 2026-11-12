package com.cernecommerce.core.domain.model.pedido;

/**
 * Por que uma linha existe na comanda (PDV-F010).
 *
 * <p>Sem isto a comanda é uma lista plana de {@code sku + quantity} e não há onde dizer se a linha
 * é a sessão em si, o segundo sabor de um duplo ou uma troca cortesia durante um consumo livre —
 * informação que o pedido gerado precisa carregar para o histórico da mesa contar a história
 * certa, e que a tela precisa para decidir se mostra "Trocar sabor" na linha.</p>
 *
 * <p><b>É ortogonal a {@code courtesy}.</b> O modo diz <i>o que</i> a linha é; a cortesia diz se
 * ela foi cobrada. {@code SABOR_EXTRA} pode ser cobrado (duplo sem promo) ou não (promo "pague 1
 * leve 2"); {@code TROCA} é sempre cortesia. Inferir um do outro pelo preço zero mentiria — um
 * desconto de 100% dá o mesmo zero.</p>
 */
public enum ConsumptionMode {

    /** Linha comum: item de balcão lançado na mesa, ou a sessão de narguilé em si. */
    NORMAL,

    /**
     * Consumo livre por valor fixo, cobrado <b>uma única vez</b>, com trocas de sabor ilimitadas.
     *
     * <p>É o único modo em que o preço <b>não</b> sai do SKU da linha: o SKU identifica qual
     * essência sair do estoque, mas o valor cobrado é o {@code openRoshPrice} do produto pai.</p>
     */
    OPEN_ROSH,

    /**
     * Segundo sabor de uma sessão dupla. Linha <b>própria</b>, não um adendo à primeira — é o que
     * deixa o consumo por sabor explícito no relatório e o que permite cobrá-la pelo seu próprio
     * preço quando a promo não está valendo.
     */
    SABOR_EXTRA,

    /**
     * Troca de sabor durante um {@link #OPEN_ROSH} já aberto. Sempre cortesia: o cliente não paga
     * de novo, mas a essência sai do estoque e o custo entra na margem.
     */
    TROCA,

    /**
     * Sessão do cardápio da mesa (PDV-F021): cobrada pelo preço da faixa (+ upgrade de vaso), com
     * SKU sintético {@code SESS-{faixa}}. Não é produto do catálogo e <b>não move estoque</b> — o
     * que ela prende são utensílios, alocados à parte.
     */
    SESSAO,

    /**
     * 2º rosh de uma {@link #SESSAO} (PDV-F021): nova essência, mesmos utensílios. De graça nos
     * dias de duplo rosh, pelo preço da faixa nos demais. Sempre ligado à sessão.
     */
    ROSH_EXTRA,

    /**
     * PDV-F061 — adicional (filtro de gelo, piteira) incluído depois que a {@link #SESSAO} já foi
     * paga: uma cobrança nova, ligada a ela. SKU sintético {@code ADIC-{id}}: não é produto do
     * catálogo e não move estoque.
     */
    ADICIONAL_SESSAO;

    /** Modos que só fazem sentido ligados a outra linha da mesma comanda. */
    public boolean requiresLinkedItem() {
        return this == SABOR_EXTRA || this == TROCA || this == ROSH_EXTRA || this == ADICIONAL_SESSAO;
    }

    /**
     * Modos da sessão baseada em produto (PDV-F010), que exigem um produto marcado como
     * {@code sessionProduct}. Os modos do cardápio (PDV-F021) não entram: não têm produto.
     */
    public boolean isSessionMode() {
        return this == OPEN_ROSH || this == SABOR_EXTRA || this == TROCA;
    }

    /** Linhas do cardápio de sessão (PDV-F021) — lançadas por {@code /sessoes}, nunca por {@code /items}. */
    public boolean isMenuSession() {
        return this == SESSAO || this == ROSH_EXTRA;
    }

    /**
     * A linha tem produto no catálogo e moveu estoque. Falso para o cardápio de sessão: o SKU é
     * sintético, então nem cashback por SKU, nem devolução de estoque no estorno se aplicam.
     */
    public boolean isCatalogLine() {
        return !isMenuSession() && this != ADICIONAL_SESSAO;
    }

    /**
     * Modos que <b>são</b> cortesia por definição, independentemente do que o cliente HTTP mandou.
     *
     * <p>Existe para o servidor não depender do cliente marcar {@code courtesy} junto: uma
     * {@code TROCA} cobrada seria narguilé vendido duas vezes na mesma sessão de valor fixo.</p>
     */
    public boolean impliesCourtesy() {
        return this == TROCA;
    }
}
