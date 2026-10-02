package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item lançado numa {@link Comanda} (PDV-F009).
 *
 * <p><b>Não reaproveita {@code OrderItem}</b>: aquele é pensado para uma venda atômica única
 * (todo o pedido nasce de uma vez, via {@code fromCatalog}), enquanto a comanda acumula linhas ao
 * longo de horas — cada uma precisa do próprio {@link #addedAt} e não carrega desconto nem taxa de
 * cashback (resolvidos só no fechamento, quando o item vira {@code OrderItem} de verdade).</p>
 *
 * <p>{@link #unitPrice}/{@link #costPrice} são congelados no instante em que o item é lançado —
 * mesma razão do {@code OrderItem}: a comanda pode ficar aberta por horas, e reprecificar um item
 * já servido no meio do caminho seria incoerente com o que o cliente já consumiu.</p>
 */
public record ComandaItem(
        Long id,
        String sku,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal costPrice,
        String productName,
        Instant addedAt,
        ConsumptionMode mode,
        boolean courtesy,
        Long linkedItemId,
        String notes,
        BigDecimal surchargeAmount,
        Long closedInOrderId,
        Integer packageUses,
        Integer packageSessionsPerUnit,
        String kitBundleId,
        Long kitTemplateId,
        BigDecimal kitDiscountAmount,
        SessionProgress session,
        SessionSetup setup) {

    /** Sufixo que a sessão de vaso grande leva na {@link #notes}, depois da essência (PDV-F021). */
    public static final String VASO_GRANDE_NOTE_SUFFIX = " · Vaso grande";

    /** Limite de {@link #notes}, casado com {@code comanda_item.notes VARCHAR(200)}. */
    public static final int NOTES_MAX_LENGTH = 200;

    public ComandaItem {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("sku é obrigatório");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity deve ser maior que zero");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice é obrigatório e não pode ser negativo");
        }
        if (costPrice != null && costPrice.signum() < 0) {
            throw new IllegalArgumentException("costPrice não pode ser negativo");
        }
        if (addedAt == null) {
            throw new IllegalArgumentException("addedAt é obrigatório");
        }
        mode = mode == null ? ConsumptionMode.NORMAL : mode;
        // Cortesia é preço zero por definição — espelha o CHECK ck_comanda_item_courtesy_is_free.
        // A recíproca NÃO vale: um item pode custar zero sem ser cortesia (produto de brinde
        // cadastrado a zero), e é por isso que o campo existe em vez de ser inferido do preço.
        if (courtesy && unitPrice.signum() != 0) {
            throw new IllegalArgumentException(
                    "linha de cortesia tem que ter unitPrice zero: " + unitPrice);
        }
        if (linkedItemId != null && !mode.requiresLinkedItem()) {
            throw new IllegalArgumentException(
                    "linkedItemId só faz sentido em SABOR_EXTRA, TROCA ou ROSH_EXTRA: mode=" + mode);
        }
        // PDV-F011 — as três invariantes do acréscimo, espelhando os CHECKs da V116. O service
        // recusa cada uma com um código de erro próprio antes de chegar aqui; estas são a rede de
        // baixo, para nenhum caminho novo montar a linha por engano.
        if (surchargeAmount != null && surchargeAmount.signum() < 0) {
            throw new IllegalArgumentException("surchargeAmount não pode ser negativo: " + surchargeAmount);
        }
        if (surchargeAmount != null && surchargeAmount.signum() > 0) {
            if (courtesy) {
                throw new IllegalArgumentException(
                        "linha de cortesia não pode ter acréscimo: o cliente não paga a linha");
            }
            if (mode != ConsumptionMode.OPEN_ROSH) {
                throw new IllegalArgumentException(
                        "acréscimo só existe em OPEN_ROSH: mode=" + mode);
            }
        }
        if (notes != null && notes.length() > NOTES_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "notes excede " + NOTES_MAX_LENGTH + " caracteres: " + notes.length());
        }
        // EST-F027 — os dois contadores da lata andam juntos ou não existem. Um sem o outro seria
        // "3 de ?" na tela, e é o par que diz se esta linha consumiu lata ou baixou unidade —
        // decisão que o cancelamento precisa tomar meses depois, sem poder reler o catálogo.
        if ((packageUses == null) != (packageSessionsPerUnit == null)) {
            throw new IllegalArgumentException("packageUses e packageSessionsPerUnit vêm juntos");
        }
        if (packageUses != null && (packageUses <= 0 || packageSessionsPerUnit <= 0)) {
            throw new IllegalArgumentException(
                    "contadores da lata têm que ser positivos: " + packageUses + "/" + packageSessionsPerUnit);
        }
        // PDV-F019 — espelha ck_comanda_item_kit_fields (V126): pacote, modelo e desconto andam
        // juntos, e o desconto nunca passa do valor da linha.
        boolean anyKit = kitBundleId != null || kitTemplateId != null || kitDiscountAmount != null;
        if (anyKit && (kitBundleId == null || kitTemplateId == null || kitDiscountAmount == null)) {
            throw new IllegalArgumentException("kitBundleId, kitTemplateId e kitDiscountAmount vêm juntos");
        }
        if (kitDiscountAmount != null && (kitDiscountAmount.signum() < 0
                || kitDiscountAmount.compareTo(quantity.multiply(unitPrice)) > 0)) {
            throw new IllegalArgumentException("kitDiscountAmount fora do intervalo da linha: " + kitDiscountAmount);
        }
        // PDV-F023 — espelha ck_comanda_item_session_status_by_mode (V132).
        if (session != null && !mode.isMenuSession()) {
            throw new IllegalArgumentException("status de sessão só existe em SESSAO/ROSH_EXTRA: mode=" + mode);
        }
        // PDV-F024 — carvão e adicionais só em linha do cardápio de sessão.
        if (setup != null && !mode.isMenuSession()) {
            throw new IllegalArgumentException("carvão/adicionais só existem em SESSAO/ROSH_EXTRA: mode=" + mode);
        }
    }

    /**
     * Monta um item novo com o preço e o custo vindos do <b>catálogo</b>, nunca do chamador —
     * mesma garantia de {@code OrderItem.fromCatalog}.
     *
     * @throws ProductNotPricedException se o produto não tem preço a cobrar
     */
    public static ComandaItem fromCatalog(String sku, BigDecimal quantity, Pricing pricing, String productName) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        return new ComandaItem(null, sku, quantity, pricing.effectivePrice(), pricing.costPrice(),
                productName, Instant.now(), ConsumptionMode.NORMAL, false, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    /**
     * Monta uma linha de sessão de narguilé (PDV-F010): o preço vem de fora, e não do
     * {@code pricing} do SKU.
     *
     * <p>É a diferença que a feature inteira gira em torno. Em {@code OPEN_ROSH} o valor é o
     * {@code openRoshPrice} do produto <b>pai</b> — resolver pelo SKU da linha, como
     * {@link #fromCatalog} faz, cobraria o preço da variação do sabor. Em cortesia o valor é zero.
     * O {@code costPrice} continua vindo do catálogo <b>em todos os casos</b>, inclusive na
     * cortesia: é ele que faz a margem do pedido mostrar o prejuízo real da promo, e um custo nulo
     * ali mentiria sobre a pergunta de negócio por trás do open rosh.</p>
     *
     * <p>PDV-F011: {@code surchargeAmount} chega <b>já somado</b> dentro de {@code unitPrice} e é
     * guardado à parte só para o relatório conseguir separar as duas parcelas depois — a mesma
     * razão pela qual {@code OrderItem.discountAmount} é campo próprio em vez de virar um preço
     * menor. O {@code costPrice} <b>não</b> muda com o acréscimo: acréscimo é margem, não custo, e
     * é essa diferença que faz a margem responder "o open rosh está dando lucro?".</p>
     *
     * @param unitPrice já resolvido pelo chamador segundo o modo, acréscimo incluído — ver
     *        {@code ComandaService.addItem}.
     * @throws ProductNotPricedException se o produto não tem custo/preço conhecido no catálogo. A
     *         checagem continua valendo mesmo em cortesia, justamente para não gravar custo nulo.
     */
    public static ComandaItem forSession(String sku, BigDecimal quantity, BigDecimal unitPrice, Pricing pricing,
            String productName, ConsumptionMode mode, boolean courtesy, Long linkedItemId, String notes,
            BigDecimal surchargeAmount) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        return new ComandaItem(null, sku, quantity, unitPrice, pricing.costPrice(), productName,
                Instant.now(), mode, courtesy, linkedItemId, notes, surchargeAmount, null, null, null, null, null, null, null, null);
    }

    /**
     * Linha do cardápio de sessão (PDV-F021) — {@code SESSAO} ou {@code ROSH_EXTRA}, quantidade 1.
     *
     * <p>O preço vem da faixa resolvida pelo service (mais o upgrade de vaso), nunca do cliente
     * HTTP. Sem custo: a sessão não tem produto no catálogo, e a essência é texto na {@code notes}
     * — inventar um custo aqui daria margem falsa. O {@code notes} leva a essência e o vaso, que é
     * o que a casa pergunta depois do fechamento.</p>
     */
    public static ComandaItem forMenuSession(String sku, BigDecimal unitPrice, String productName,
            ConsumptionMode mode, boolean courtesy, Long linkedItemId, String notes, SessionProgress session,
            SessionSetup setup) {
        if (mode == null || !mode.isMenuSession()) {
            throw new IllegalArgumentException("linha do cardápio de sessão exige SESSAO ou ROSH_EXTRA: " + mode);
        }
        if (session == null) {
            throw new IllegalArgumentException("linha do cardápio de sessão nasce com status (PDV-F023)");
        }
        return new ComandaItem(null, sku, BigDecimal.ONE, unitPrice, null, productName, Instant.now(), mode,
                courtesy, linkedItemId, notes, null, null, null, null, null, null, null, session,
                setup == null || setup.isEmpty() ? null : setup);
    }

    /** Reconstitui um item a partir de persistência. */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, ConsumptionMode.NORMAL,
                false, null);
    }

    /** Reconstitui um item a partir de persistência, com o modo da sessão (PDV-F010). */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                null, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com o setup da mesa e o acréscimo (PDV-F011).
     * Linha anterior à V116 lê os dois como {@code null} — que é a verdade, e não "não teve".
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com o pedido que já o cobrou (PDV-F017).
     * {@code closedInOrderId} nulo é a linha ainda em aberto — o estado de toda linha antes da V121.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, null, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com o contador da lata (EST-F027). Linha
     * anterior à V124 lê os dois como {@code null} — que é a verdade: ela baixou uma unidade
     * inteira, e é assim que ela tem que ser desfeita se a mesa for cancelada.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, null, null, null);
    }

    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com o status da sessão (PDV-F023). Linha de
     * catálogo, e linha de sessão anterior à V132 sem backfill, lê {@code session} nulo.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, null);
    }

    /** Reconstitui um item a partir de persistência, com carvão e adicionais (PDV-F024). */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup);
    }

    /**
     * A linha ainda não foi cobrada por nenhum pedido (PDV-F017).
     *
     * <p>Com conta dividida uma comanda tem linhas em dois estados ao mesmo tempo, e é este
     * predicado — não o status da comanda — que diz o que ainda falta pagar.</p>
     */
    public boolean isOpen() {
        return closedInOrderId == null;
    }

    /** Marca a linha como cobrada pelo pedido informado. Cópia — o item permanece imutável. */
    public ComandaItem closedIn(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId é obrigatório para fechar a linha da comanda");
        }
        if (closedInOrderId != null) {
            throw new IllegalStateException(
                    "item " + id + " já foi cobrado pelo pedido " + closedInOrderId);
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, orderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, setup);
    }

    /**
     * Carimba na linha qual uso da lata ela foi (EST-F027) — "a 3ª de 5".
     *
     * <p>É snapshot, não referência: guarda o contador <b>no instante do lançamento</b>, e é o que
     * permite a tela mostrar "3 de 5" por linha sem uma segunda chamada, e o histórico continuar
     * verdadeiro depois que a lata for reposta. Também é o que diz, meses depois, que esta linha
     * consumiu lata e não unidade — informação que o cancelamento precisa e que reler o catálogo
     * não daria, porque o cadastro pode ter mudado desde então.</p>
     */
    public ComandaItem withPackageCounter(int uses, int sessionsPerUnit) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, uses, sessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, setup);
    }

    /**
     * PDV-F019 — marca a linha como item de um kit montável, com a parte do desconto do kit que
     * coube a ela. O preço cheio continua em {@code unitPrice}: o desconto é somado ao desconto de
     * conta no fechamento e vai para {@code OrderItem.discountAmount}, onde cashback e margem já o
     * enxergam.
     */
    public ComandaItem withKit(String bundleId, Long templateId, BigDecimal discountAmount) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                bundleId, templateId, discountAmount, session, setup);
    }

    /**
     * PDV-F023 — avança o status da sessão. Cópia — o item permanece imutável.
     *
     * @throws IllegalStateException se a linha não tem status ou a transição não é permitida
     */
    public ComandaItem withSessionStatus(SessionStatus next, Instant at) {
        if (session == null) {
            throw new IllegalStateException("item " + id + " não tem status de sessão");
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session.advanceTo(next, at), setup);
    }

    /**
     * PDV-F027 — a faixa de uma linha do cardápio de sessão, lida do SKU sintético {@code SESS-{id}}.
     * Nulo em linha de catálogo ou SKU fora do padrão.
     */
    public Long sessionTierId() {
        if (!mode.isMenuSession() || !sku.startsWith(SessionTier.SKU_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(sku.substring(SessionTier.SKU_PREFIX.length()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** PDV-F027 — a essência da linha de sessão: a nota sem o sufixo de vaso. Nulo fora de sessão. */
    public String sessionEssencia() {
        if (!mode.isMenuSession() || notes == null) {
            return null;
        }
        return notes.endsWith(VASO_GRANDE_NOTE_SUFFIX)
                ? notes.substring(0, notes.length() - VASO_GRANDE_NOTE_SUFFIX.length())
                : notes;
    }

    /**
     * PDV-F027 — a sessão foi paga: sai de {@code AGUARDANDO_PAGAMENTO} para o preparo, e o tempo de
     * mesa começa. Linha em outro status volta como está — pagar não mexe no ciclo físico dela.
     */
    public ComandaItem withSessionPaid(Instant at) {
        if (session == null || !session.isAwaitingPayment()) {
            return this;
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session.paid(at), setup);
    }

    /**
     * A sessão ainda está no salão — tudo que não foi recolhido. Linha de sessão sem status (anterior
     * à V132) conta como recolhida: o backfill já a resolveu, e não há como o operador avançá-la.
     */
    public boolean isActiveSession() {
        return mode.isMenuSession() && session != null && !session.isCollected();
    }

    public SessionStatus sessionStatus() {
        return session == null ? null : session.status();
    }

    /** PDV-F034 — sessão (ou rosh dela) que foi ao salão antes de paga. */
    public boolean isPayLater() {
        return session != null && session.payLater();
    }

    public boolean inKit() {
        return kitBundleId != null;
    }

    /** Desconto do kit montável nesta linha; zero fora de kit. */
    public BigDecimal kitDiscount() {
        return kitDiscountAmount == null ? BigDecimal.ZERO : kitDiscountAmount;
    }

    /** {@link #subtotal()} menos o desconto do kit — o que a linha cobra antes do desconto de conta. */
    public BigDecimal netSubtotal() {
        return subtotal().subtract(kitDiscount());
    }

    /**
     * A linha consumiu uso de lata aberta, e não uma unidade do saldo (EST-F027).
     *
     * <p>Quem cancela ou remove a linha decide por aqui: lata se desfaz decrementando o contador,
     * unidade se desfaz com {@code ENTRADA}. Confundir os dois inventa saldo que não existe.</p>
     */
    public boolean consumedPackage() {
        return packageUses != null;
    }

    /** {@code quantity * unitPrice}. */
    public BigDecimal subtotal() {
        return quantity.multiply(unitPrice).setScale(Money.MONEY_SCALE, Money.ROUNDING);
    }
}
