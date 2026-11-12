package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
        SessionSetup setup,
        String essenceSku,
        String essenceSku2,
        boolean essence2Package,
        ProporcaoMistura essenceRatio,
        boolean storePurchase,
        BigDecimal storeDiscountAmount,
        Long promotionId,
        String promotionName,
        BigDecimal promotionDiscountAmount,
        SessionModality modality,
        SessionLink sessionLink,
        boolean benefitsExcluded) {

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
        // PDV-F061 — espelha ck_comanda_item_session_link e ck_comanda_item_linked_by_mode (V191): o
        // pacote da compra de essência é linha NORMAL ligada à sessão; o adicional pós-entrega tem modo
        // próprio. Os dois sempre apontam para a sessão.
        if ((sessionLink == SessionLink.PACOTE_ESSENCIA || sessionLink == SessionLink.PACOTE_INCLUSO)
                && mode != ConsumptionMode.NORMAL) {
            throw new IllegalArgumentException("pacote da sessão é linha NORMAL: mode=" + mode);
        }
        if (sessionLink == SessionLink.ADICIONAL && mode != ConsumptionMode.ADICIONAL_SESSAO) {
            throw new IllegalArgumentException("adicional pós-entrega é linha ADICIONAL_SESSAO: mode=" + mode);
        }
        if (sessionLink != null && linkedItemId == null) {
            throw new IllegalArgumentException("linha ligada à sessão exige linkedItemId");
        }
        if (mode == ConsumptionMode.ADICIONAL_SESSAO && sessionLink != SessionLink.ADICIONAL) {
            throw new IllegalArgumentException("ADICIONAL_SESSAO exige o vínculo ADICIONAL");
        }
        if (linkedItemId != null && !mode.requiresLinkedItem() && !isPackageLink(sessionLink)) {
            throw new IllegalArgumentException(
                    "linkedItemId só faz sentido em SABOR_EXTRA, TROCA, ROSH_EXTRA ou linha ligada à sessão: mode="
                            + mode);
        }
        if (modality != null && mode != ConsumptionMode.SESSAO) {
            throw new IllegalArgumentException("modalidade só existe na linha SESSAO: mode=" + mode);
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
        // PDV-F042 — espelha ck_comanda_item_essence_sku_menu_session (V147): a linha de catálogo já
        // é o próprio produto, e só a sessão do cardápio (SKU sintético da faixa) precisa dizer qual
        // essência queimou.
        if (essenceSku != null && (essenceSku.isBlank() || !mode.isMenuSession())) {
            throw new IllegalArgumentException("essenceSku só existe em SESSAO/ROSH_EXTRA: mode=" + mode);
        }
        // PDV-F051 — espelha ck_comanda_item_essence_sku2 (V160): o 2º sabor da mescla só existe com
        // o 1º, e é outro sabor. A marca de lata do 2º não existe sem ele.
        if (essenceSku2 != null && (essenceSku2.isBlank() || essenceSku == null || essenceSku2.equals(essenceSku))) {
            throw new IllegalArgumentException("essenceSku2 exige essenceSku e outro sabor: " + essenceSku2);
        }
        if (essenceSku2 == null && essence2Package) {
            throw new IllegalArgumentException("essence2Package sem essenceSku2");
        }
        // Espelha ck_comanda_item_essence_ratio (V169): a proporção é da mescla.
        if (essenceRatio != null && essenceSku2 == null) {
            throw new IllegalArgumentException("essenceRatio sem essenceSku2");
        }
        // PDV-F060 — espelha ck_comanda_item_store_purchase (V178): compra na loja é linha de catálogo
        // comum, e o desconto dela só existe nela, dentro do valor da linha.
        if (storePurchase && mode != ConsumptionMode.NORMAL) {
            throw new IllegalArgumentException("compra na loja só existe em linha NORMAL: mode=" + mode);
        }
        if (storeDiscountAmount != null && (!storePurchase || storeDiscountAmount.signum() < 0
                || storeDiscountAmount.compareTo(quantity.multiply(unitPrice)) > 0)) {
            throw new IllegalArgumentException("storeDiscountAmount inválido: " + storeDiscountAmount);
        }
        // Promoção por quantidade: recalculada pelo servidor a cada item que entra ou sai da comanda.
        promotionDiscountAmount = promotionDiscountAmount == null ? BigDecimal.ZERO : promotionDiscountAmount;
        if (promotionDiscountAmount.signum() < 0) {
            throw new IllegalArgumentException("promotionDiscountAmount não pode ser negativo");
        }
        if (promotionDiscountAmount.signum() > 0 && mode != ConsumptionMode.NORMAL) {
            throw new IllegalArgumentException("promoção por quantidade só em linha NORMAL: mode=" + mode);
        }
        if (promotionDiscountAmount.signum() == 0) {
            promotionId = null;
            promotionName = null;
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
                null, null, null, null, null, false, null, false, null, null, null, null, null, null, false);
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
                Instant.now(), mode, courtesy, linkedItemId, notes, surchargeAmount, null, null, null, null, null, null, null, null, null,
                null, false, null, false, null, null, null, null, null, null, false);
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
                setup == null || setup.isEmpty() ? null : setup, null, null, false, null, false, null, null, null, null, null, null, false);
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
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, setup, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com a essência do catálogo consumida pela linha
     * de sessão do cardápio (PDV-F042). Linha anterior à V147 lê {@code null}: essência só em texto.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, setup, essenceSku, null, false);
    }

    /**
     * Reconstitui um item a partir de persistência, com o 2º sabor da sessão mesclada (PDV-F051).
     * Linha anterior à V160 lê {@code null}/{@code false}: um sabor só.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku,
            String essenceSku2, boolean essence2Package) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com a proporção da mescla (V169). Linha anterior
     * lê {@code null}: meio a meio implícito, ou um sabor só.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku,
            String essenceSku2, boolean essence2Package, ProporcaoMistura essenceRatio) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId,
                notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package,
                essenceRatio, false, null);
    }

    /**
     * Reconstitui um item a partir de persistência, com a marca de compra na loja e o desconto dela
     * (PDV-F060, V178). Linha anterior lê {@code false}/{@code null}: consumo do salão.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku,
            String essenceSku2, boolean essence2Package, ProporcaoMistura essenceRatio, boolean storePurchase,
            BigDecimal storeDiscountAmount) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId, notes,
                surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId, kitTemplateId,
                kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio,
                storePurchase, storeDiscountAmount, null, null, null);
    }

    /** Reconstitui um item com a promoção por quantidade gravada na linha (V180). */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku,
            String essenceSku2, boolean essence2Package, ProporcaoMistura essenceRatio, boolean storePurchase,
            BigDecimal storeDiscountAmount, Long promotionId, String promotionName, BigDecimal promotionDiscountAmount) {
        return of(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy, linkedItemId, notes,
                surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId, kitTemplateId,
                kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio,
                storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, null, null);
    }

    /** Reconstitui um item com a modalidade da sessão e o vínculo da linha ligada a ela (V191). */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku,
            String essenceSku2, boolean essence2Package, ProporcaoMistura essenceRatio, boolean storePurchase,
            BigDecimal storeDiscountAmount, Long promotionId, String promotionName, BigDecimal promotionDiscountAmount,
            SessionModality modality, SessionLink sessionLink) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName,
                promotionDiscountAmount, modality, sessionLink, false);
    }

    /**
     * Reconstitui um item com a marcação de fora dos benefícios (V198) — carimbada no lançamento, como o
     * preço: a linha de categoria fora dos benefícios sai da base do desconto do chefe e do resgate no
     * fechamento da mesa.
     */
    public static ComandaItem of(Long id, String sku, BigDecimal quantity, BigDecimal unitPrice,
            BigDecimal costPrice, String productName, Instant addedAt, ConsumptionMode mode, boolean courtesy,
            Long linkedItemId, String notes, BigDecimal surchargeAmount, Long closedInOrderId,
            Integer packageUses, Integer packageSessionsPerUnit, String kitBundleId, Long kitTemplateId,
            BigDecimal kitDiscountAmount, SessionProgress session, SessionSetup setup, String essenceSku,
            String essenceSku2, boolean essence2Package, ProporcaoMistura essenceRatio, boolean storePurchase,
            BigDecimal storeDiscountAmount, Long promotionId, String promotionName, BigDecimal promotionDiscountAmount,
            SessionModality modality, SessionLink sessionLink, boolean benefitsExcluded) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName,
                promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /** V198 — carimba se a linha é de categoria fora dos benefícios. Cópia — o item permanece imutável. */
    public ComandaItem withBenefitsExcluded(boolean excluded) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName,
                promotionDiscountAmount, modality, sessionLink, excluded);
    }

    /**
     * PDV-F061 — pacotes da loja comprados com a sessão das modalidades novas: linha de catálogo comum
     * (preço de venda e custo do SKU), ligada à sessão. A baixa é do service, no depósito da comanda.
     */
    public static ComandaItem forEssencePackage(String sku, Pricing pricing, String productName, Long sessionItemId,
            int quantity) {
        if (pricing == null || !pricing.isPriced()) {
            throw new ProductNotPricedException(sku);
        }
        return new ComandaItem(null, sku, BigDecimal.valueOf(quantity), pricing.effectivePrice(), pricing.costPrice(),
                productName, Instant.now(), ConsumptionMode.NORMAL, false, sessionItemId, null, null, null, null,
                null, null, null, null, null, null, null, null, false, null, false, null, null, null, null, null,
                SessionLink.PACOTE_ESSENCIA, false);
    }

    /**
     * PDV-F061 — o pacote do sabor principal que vem no kit de itens pessoais: quantidade 1 a R$ 0, sem
     * ser cortesia (não pede alçada). O custo do catálogo fica, para a margem mostrar o que a casa deu.
     */
    public static ComandaItem forIncludedPackage(String sku, Pricing pricing, String productName,
            Long sessionItemId) {
        return new ComandaItem(null, sku, BigDecimal.ONE, BigDecimal.ZERO,
                pricing == null ? null : pricing.costPrice(), productName, Instant.now(), ConsumptionMode.NORMAL,
                false, sessionItemId, null, null, null, null, null, null, null, null, null, null, null, null, false,
                null, false, null, null, null, null, null, SessionLink.PACOTE_INCLUSO, false);
    }

    /**
     * PDV-F061 — o adicional incluído depois que a sessão já foi paga: uma cobrança nova, em aberto,
     * ligada a ela. SKU sintético {@code ADIC-{id}}, sem custo e sem estoque.
     */
    public static ComandaItem forAfterDeliveryAddon(SessionAddon addon, Long sessionItemId) {
        return new ComandaItem(null, SessionAddon.SKU_PREFIX + addon.id(), BigDecimal.ONE, addon.preco(), null,
                addon.nome(), Instant.now(), ConsumptionMode.ADICIONAL_SESSAO, false, sessionItemId, null, null, null,
                null, null, null, null, null, null, null, null, null, false, null, false, null, null, null, null,
                null, SessionLink.ADICIONAL, false);
    }

    /** PDV-F061 — grava a modalidade na linha de sessão recém-montada. */
    public ComandaItem withModality(SessionModality newModality) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName,
                promotionDiscountAmount, newModality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F061 — o adicional que entra na sessão ainda não cobrada depois da entrega: soma o preço
     * dele ao valor já gravado (sem reprecificar pela faixa) e o acrescenta ao setup e ao nome.
     */
    public ComandaItem withAddedAddon(SessionAddon addon) {
        if (mode != ConsumptionMode.SESSAO || session == null) {
            throw new IllegalStateException("item " + id + " não é uma linha de sessão");
        }
        SessionSetup current = setup == null ? SessionSetup.of(null, List.of()) : setup;
        return new ComandaItem(id, sku, quantity, unitPrice.add(addon.preco()), costPrice,
                productName + " + " + addon.nome(), addedAt, mode, courtesy, linkedItemId, notes, surchargeAmount,
                closedInOrderId, packageUses, packageSessionsPerUnit, kitBundleId, kitTemplateId, kitDiscountAmount,
                session, current.withAddon(addon), essenceSku, essenceSku2, essence2Package, essenceRatio,
                storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality,
                sessionLink, benefitsExcluded);
    }

    /** PDV-F061 — a modalidade da sessão; linha antiga (sem coluna) é a padrão. */
    public SessionModality effectiveModality() {
        return modality == null ? SessionModality.PADRAO : modality;
    }

    /** PDV-F061 — pacote da loja ligado à sessão das modalidades novas (cobrado ou incluso). */
    public boolean isPackageLine() {
        return isPackageLink(sessionLink);
    }

    /** PDV-F061 — o pacote incluso no kit de itens pessoais, a R$ 0. */
    public boolean isIncludedPackage() {
        return sessionLink == SessionLink.PACOTE_INCLUSO;
    }

    private static boolean isPackageLink(SessionLink link) {
        return link == SessionLink.PACOTE_ESSENCIA || link == SessionLink.PACOTE_INCLUSO;
    }

    /**
     * PDV-F044 — o 2º rosh do duplo lançado sem sabor: o cliente decide quando a 1ª sessão acaba.
     *
     * <p>Derivado, não coluna: até aqui todo {@code ROSH_EXTRA} nascia com a essência na
     * {@link #notes} ({@code sessionNotes} recusa vazio), então nota nula num rosh só existe por este
     * caminho, e as linhas antigas leem {@code false} sem migration. Só enquanto está no salão: o
     * rosh pendente que sai dele (removido ou desistido) não espera mais sabor nenhum.</p>
     */
    public boolean isEssencePending() {
        return mode == ConsumptionMode.ROSH_EXTRA && notes == null && isActiveSession();
    }

    /**
     * PDV-F044 — grava o sabor escolhido no 2º rosh pendente: a faixa (SKU sintético e nome) e a
     * essência na nota. Preço e cortesia não mudam — a faixa é registro e custo. A baixa da lata é
     * do service, por {@link #withPackageCounter} e {@link #withEssence}, como no lançamento.
     */
    public ComandaItem withRoshFlavor(String tierSku, String productName, String notes) {
        if (!isEssencePending()) {
            throw new IllegalStateException("item " + id + " não é um 2º rosh aguardando sabor");
        }
        if (notes == null || notes.isBlank()) {
            throw new IllegalArgumentException("o sabor do 2º rosh é obrigatório");
        }
        return new ComandaItem(id, tierSku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
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
                kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
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
                kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F042 — carimba na linha de sessão do cardápio qual essência do catálogo ela queimou. É o
     * que o desfazimento lê meses depois: o SKU da linha é o da faixa ({@code SESS-{id}}), que não
     * tem estoque.
     */
    public ComandaItem withEssence(String essenceSku) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F051 — carimba o 2º sabor da sessão mesclada e se ele saiu de lata aberta ({@code true}) ou
     * como unidade do saldo. É o par que o desfazimento lê, como {@link #consumedPackage()} no 1º.
     */
    public ComandaItem withSecondEssence(String essenceSku2, boolean fromPackage) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                fromPackage, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * Carimba quanto de cada sabor vai na mescla. Sem 2º sabor fica nula — um sabor só não tem
     * proporção.
     */
    public ComandaItem withEssenceRatio(ProporcaoMistura ratio) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceSku2 == null ? null : ratio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /** PDV-F051 — a sessão tem dois sabores mesclados. */
    public boolean isMixed() {
        return essenceSku2 != null;
    }

    /**
     * A linha tirou algo do estoque (ou de uma lata) ao ser lançada: toda linha de catálogo, e a
     * sessão do cardápio só quando tem essência do catálogo (PDV-F042). É a guarda do desfazimento —
     * sessão só com texto nunca baixou nada e não tem o que devolver.
     */
    public boolean consumedStock() {
        // PDV-F061 — a sessão das modalidades novas carimba o sabor sem gastar lata: quem baixou estoque
        // foram as linhas de pacote, que se desfazem por conta própria.
        if (mode == ConsumptionMode.SESSAO && effectiveModality().usesStorePackages()) {
            return false;
        }
        return mode.isCatalogLine() || essenceSku != null;
    }

    /**
     * PDV-F042 — a essência desta sessão já foi queimada: a linha saiu da espera (preparando,
     * entregue ou recolhida — o que inclui a desistida). Daqui em diante desfazer a linha não
     * devolve nada ao estoque nem à lata: a essência não volta para a prateleira, e devolvê-la
     * inventaria saldo. Linha de catálogo e sessão sem status nunca são "queimadas" por aqui.
     */
    public boolean essenceBurned() {
        return mode.isMenuSession() && session != null && (session.isServed() || session.isCollected());
    }

    /** O SKU que o estoque conhece para esta linha: a essência, na sessão do cardápio; o próprio, no resto. */
    public String stockSku() {
        return essenceSku != null ? essenceSku : sku;
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
                bundleId, templateId, discountAmount, session, setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F060 — a linha é compra na loja lançada na conta da mesa: cobrada junto com a sessão paga no
     * final e fora da base da taxa de serviço. O desconto (do kit montável do caixa) vai na linha, como
     * na venda do caixa; nulo ou zero é sem desconto.
     */
    public ComandaItem asStorePurchase(BigDecimal discountAmount) {
        BigDecimal discount = discountAmount == null || discountAmount.signum() == 0 ? null : discountAmount;
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceRatio, true, discount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F058 — a sessão editada: faixa, preço, nome, nota (essência e vaso) e o setup mudam; id,
     * status, ligação, cobrança e a marca de pagar no final ficam. Os sabores do catálogo e o contador
     * da lata são zerados aqui e recarimbados pelo service com a baixa nova (ou repostos, se não mudaram).
     */
    public ComandaItem withSessionConfig(String tierSku, BigDecimal newUnitPrice, String newProductName,
            String newNotes, SessionSetup newSetup) {
        if (mode != ConsumptionMode.SESSAO || session == null) {
            throw new IllegalStateException("item " + id + " não é uma linha de sessão");
        }
        return new ComandaItem(id, tierSku, quantity, newUnitPrice, costPrice, newProductName, addedAt, mode,
                courtesy, linkedItemId, newNotes, surchargeAmount, closedInOrderId, null, null, kitBundleId,
                kitTemplateId, kitDiscountAmount, session, newSetup == null || newSetup.isEmpty() ? null : newSetup,
                null, null, false, null, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F058 — repõe na linha editada o carimbo de essência e lata de antes, quando os sabores não
     * mudaram: nada de estoque se mexeu, e o desfazimento continua precisando saber o que ela queimou.
     */
    public ComandaItem withEssenceStampFrom(ComandaItem before) {
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, before.packageUses,
                before.packageSessionsPerUnit, kitBundleId, kitTemplateId, kitDiscountAmount, session, setup,
                before.essenceSku, before.essenceSku2, before.essence2Package, essenceRatio, storePurchase,
                storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /**
     * PDV-F059 — a sessão aguardando pagamento passa a pagar no final: vai ao preparo e o tempo começa.
     * O rosh ligado herda a marca por {@link #withPayLaterInherited()}.
     */
    public ComandaItem convertedToPayLater(Instant at) {
        if (session == null) {
            throw new IllegalStateException("item " + id + " não tem status de sessão");
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session.convertedToPayLater(at), setup, essenceSku,
                essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    /** PDV-F059 — o 2º rosh herda a marca de pagar no final; o status dele não muda. */
    public ComandaItem withPayLaterInherited() {
        if (session == null || session.payLater() || session.isAwaitingPayment()) {
            return this;
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session.withPayLater(), setup, essenceSku,
                essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
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
                kitBundleId, kitTemplateId, kitDiscountAmount, session.advanceTo(next, at), setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
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
    /**
     * PDV-C036 — a linha exige desistência (motivo) em vez de ser apagada: sessão do cardápio, ainda
     * não cobrada, não cortesia, e já servida. Apagá-la era dar o narguilé de graça sem a alçada de
     * cortesia e sem deixar rastro no histórico.
     */
    public boolean requiresWithdrawal() {
        return mode.isMenuSession() && isOpen() && !courtesy && session != null && session.isServed();
    }

    public boolean isWithdrawn() {
        return session != null && session.isWithdrawn();
    }

    /**
     * PDV-C036 — registra a desistência: a linha fica, recolhida, como cortesia a R$ 0 (não é mais
     * dívida — ver {@code Comanda.owedItems}), com o motivo e quem registrou.
     */
    public ComandaItem withdrawn(String reason, String by, Instant at) {
        if (!requiresWithdrawal()) {
            throw new IllegalStateException("item " + id + " não é uma sessão servida a cobrar");
        }
        return new ComandaItem(id, sku, quantity, BigDecimal.ZERO, costPrice, productName, addedAt, mode, true,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session.withdrawn(reason, by, at), setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
    }

    public ComandaItem withSessionPaid(Instant at) {
        if (session == null || !session.isAwaitingPayment()) {
            return this;
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session.paid(at), setup, essenceSku, essenceSku2, essence2Package, essenceRatio, storePurchase, storeDiscountAmount, promotionId, promotionName, promotionDiscountAmount, modality, sessionLink, benefitsExcluded);
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

    /**
     * Grava o desconto da promoção por quantidade que coube a esta linha. Substitui o anterior: a
     * comanda inteira é recalculada a cada mudança. Zero tira a promoção.
     */
    public ComandaItem withPromotion(Long newPromotionId, String newPromotionName, BigDecimal amount) {
        BigDecimal promo = amount == null ? BigDecimal.ZERO : Money.of(amount);
        if (promo.add(kitDiscount()).add(storeDiscount()).compareTo(subtotal()) > 0) {
            throw new IllegalArgumentException("desconto da linha passaria do valor dela: " + promo);
        }
        return new ComandaItem(id, sku, quantity, unitPrice, costPrice, productName, addedAt, mode, courtesy,
                linkedItemId, notes, surchargeAmount, closedInOrderId, packageUses, packageSessionsPerUnit,
                kitBundleId, kitTemplateId, kitDiscountAmount, session, setup, essenceSku, essenceSku2,
                essence2Package, essenceRatio, storePurchase, storeDiscountAmount, newPromotionId, newPromotionName,
                promo, modality, sessionLink, benefitsExcluded);
    }

    public ComandaItem withoutPromotion() {
        return promotionDiscount().signum() == 0 ? this : withPromotion(null, null, BigDecimal.ZERO);
    }

    /** Desconto de promoção por quantidade nesta linha; zero sem promoção. */
    public BigDecimal promotionDiscount() {
        return promotionDiscountAmount == null ? BigDecimal.ZERO : promotionDiscountAmount;
    }

    public boolean inKit() {
        return kitBundleId != null;
    }

    /** Desconto do kit montável nesta linha; zero fora de kit. */
    public BigDecimal kitDiscount() {
        return kitDiscountAmount == null ? BigDecimal.ZERO : kitDiscountAmount;
    }

    /** PDV-F060 — desconto da compra na loja nesta linha; zero fora dela. */
    public BigDecimal storeDiscount() {
        return storeDiscountAmount == null ? BigDecimal.ZERO : storeDiscountAmount;
    }

    /**
     * O desconto que a linha já trouxe — kit montável (PDV-F019), compra na loja (PDV-F060) ou
     * promoção por quantidade — e que o fechamento soma ao rateio do desconto de conta.
     */
    public BigDecimal lineDiscount() {
        return kitDiscount().add(storeDiscount()).add(promotionDiscount());
    }

    /** {@link #subtotal()} menos o desconto da linha — o que ela cobra antes do desconto de conta. */
    public BigDecimal netSubtotal() {
        return subtotal().subtract(lineDiscount());
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
