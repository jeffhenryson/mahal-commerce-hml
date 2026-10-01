package com.cernecommerce.core.service;

import com.cernecommerce.core.ports.in.ComandaUseCase.PorHora;
import com.cernecommerce.core.ports.in.ComandaUseCase.PorMesa;
import com.cernecommerce.core.ports.in.ComandaUseCase.PorAtendente;
import com.cernecommerce.core.ports.in.ComandaUseCase.SessoesNarguile;
import com.cernecommerce.core.ports.in.ComandaUseCase.ComandaAnalytics;
import com.cernecommerce.core.ports.in.ComandaUseCase.ComandaHistoryEntry;
import java.util.TreeMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Comparator;
import java.time.Duration;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.core.domain.model.pdv.ClosedComanda;
import com.cernecommerce.core.domain.exception.pdv.LegacySessionDisabledException;
import com.cernecommerce.core.domain.exception.pdv.NotASessionLineException;
import com.cernecommerce.core.domain.exception.pdv.CatalogItemNotAllowedOnTableException;
import com.cernecommerce.core.domain.exception.pdv.ComandaHasOpenItemsException;
import com.cernecommerce.core.domain.exception.pdv.InvalidSessionTransitionException;
import com.cernecommerce.core.domain.exception.pdv.SessionNotCollectedException;
import com.cernecommerce.core.domain.model.pdv.SessionAddon;
import com.cernecommerce.core.domain.model.pdv.SessionProgress;
import com.cernecommerce.core.domain.model.pdv.SessionSetup;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionAssetType;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.exception.pdv.ComandaEmptyException;
import com.cernecommerce.core.domain.exception.pdv.ComandaItemNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.ComandaMergeNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.ComandaPartiallyClosedException;
import com.cernecommerce.core.domain.exception.pdv.ComandaNotOpenException;
import com.cernecommerce.core.domain.exception.pdv.ComandaOnlyCourtesyException;
import com.cernecommerce.core.domain.exception.pdv.DiscountExceedsBillException;
import com.cernecommerce.core.domain.exception.pdv.KitItemRemovalNotAllowedException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemIsChargedException;
import com.cernecommerce.core.domain.exception.pdv.ItemNotOpenInComandaException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemMustCloseTogetherException;
import com.cernecommerce.core.domain.exception.pdv.LinkedItemRequiredException;
import com.cernecommerce.core.domain.exception.pdv.NotASessionProductException;
import com.cernecommerce.core.domain.exception.pdv.NotAnOpenRoshException;
import com.cernecommerce.core.domain.exception.pdv.NotAvailableForTableException;
import com.cernecommerce.core.domain.exception.pdv.NotesTooLongException;
import com.cernecommerce.core.domain.exception.pdv.SessionEssenceRequiredException;
import com.cernecommerce.core.domain.exception.pdv.MenuSessionNotAllowedOnItemsException;
import com.cernecommerce.core.domain.exception.pdv.OpenRoshNotPricedException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeInvalidException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeNotApplicableException;
import com.cernecommerce.core.domain.exception.pdv.SurchargeOnCourtesyException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.DiscountProration;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comanda de mesa (PDV-F009): pedidos incrementais de uma sessão de caixa aberta por horas — o
 * caso do lounge de narguilé. Endpoints novos, sem tocar em {@code PdvService.registerSale}.
 *
 * <h2>Baixa de estoque imediata, não atômica ao longo da vida da comanda</h2>
 * <p>Cada {@link #addItem} debita o estoque na hora, no mesmo instante em que o item é lançado —
 * reflete o evento físico real (a essência foi preparada e servida). Diferente de
 * {@code registerSale}, que debita tudo numa única transação no fechamento, aqui cada chamada é
 * seu próprio commit: não dá para segurar uma transação de banco aberta pelas horas em que uma
 * comanda fica em uso. A contrapartida é que itens já lançados <b>não</b> fazem rollback se um
 * lançamento posterior falhar — {@link #cancelComanda} cobre o abandono explícito, devolvendo cada
 * item ao estoque, mas não há varredura automática para comanda esquecida aberta sem cancelamento.
 * Limitação conhecida, documentada no README do módulo.</p>
 *
 * <h2>Caixa por atendente, mesas compartilhadas (PDV-F010)</h2>
 * <p><b>Abrir</b> uma comanda exige a própria sessão — a mesa nasce na gaveta de quem a abriu, e é
 * esse depósito que vai baixar estoque. <b>Operar</b> uma mesa já aberta (lançar, fechar, cancelar)
 * não exige posse: o atendente que assume o posto do colega precisa enxergar e tocar as mesas do
 * salão. O controle de acesso ali é a permissão {@code PDV_COMANDA_MANAGE}, não a posse da gaveta.</p>
 *
 * <p>Não é regressão do isolamento de PDV-C004: aquele resolveu a <i>venda de balcão</i>, onde
 * vender no caixa alheio criava diferença sem dono, e {@code registerSale} e os movimentos de caixa
 * continuam exigindo posse. O consumo da mesa é do salão, não do operador.</p>
 *
 * <p>A contrapartida é <b>onde o dinheiro entra</b>: o pedido nasce na sessão de <b>quem fecha</b>
 * (via {@code getCurrentSession}), não na que abriu a comanda. É a leitura que bate com a
 * conferência física — a cédula está na gaveta de quem recebeu — e é o que impede o pedido de cair
 * numa sessão que o colega já encerrou.</p>
 *
 * <h2>Mesa compartilhada exige trava na leitura (PDV-C008)</h2>
 * <p>Abrir a mesa para qualquer atendente teve um preço que só aparece sob concorrência: lançar,
 * fechar e cancelar <b>decidem sobre o estado que leram</b> — {@code requireOpen} é a decisão — e
 * duas dessas decisões tomadas em paralelo sobre a mesma comanda se contradizem. B fecha e gera o
 * pedido; a leitura de A continua dizendo ABERTA, e o item de A entra numa mesa cujo pedido já foi
 * pago. O estoque saiu no commit do {@code addItem}, e ninguém é cobrado por ele.</p>
 *
 * <p>Por isso os três caminhos passam por {@code getComandaForUpdate}, e só a consulta de tela
 * continua lendo sem trava. <b>Um {@code @Version} não resolveria:</b> a versão otimista só colide
 * quando o UPDATE é emitido, e o Hibernate compara o agregado com o snapshot que ele mesmo
 * carregou — regravar estado velho por cima não conta como alteração, nenhum UPDATE sai, nenhuma
 * colisão aparece. A corrida é sobre a leitura, e é lá que a trava tem que estar.</p>
 *
 * <h2>Reaproveita {@code PdvService}, não duplica</h2>
 * <p>Posse de sessão e validação de pagamento/troco são as mesmas regras da venda de balcão — a de
 * troco em pagamento dividido, em particular, já foi endurecida uma vez (mais estrita que o
 * desenho original do plano). Duplicá-la aqui arriscaria as duas cópias divergirem em silêncio, o
 * tipo de bug que as tabelas de Regras de Negócio deste projeto existem para prevenir. Por isso
 * este service recebe o bean <b>concreto</b> {@code PdvService} (não a interface {@code PdvUseCase},
 * que esconderia os métodos package-private) e chama {@code requireOwnOpenSession}/
 * {@code validatePaymentsAndComputeChange} diretamente — primeira dependência service-para-service
 * do projeto, deliberada.</p>
 */
public class ComandaService implements ComandaUseCase {

    private final ComandaRepository comandaRepository;
    private final EstoqueUseCase estoqueUseCase;
    private final OrderRepository orderRepository;
    private final OrderPaymentRepository orderPaymentRepository;
    private final CashbackUseCase cashbackUseCase;
    private final PdvService pdvService;

    /** Quem pode agir sobre uma mesa é quem recebe o aviso de que existe uma esquecida. */
    private static final String COMANDA_MANAGE_PERMISSION = "PDV_COMANDA_MANAGE";

    /** PDV-F013 — destinatários do alerta de mesa esquecida e o canal por onde ele sai. */
    private final NotificationUseCase notificationUseCase;
    private final UserRepository userRepository;

    /**
     * PDV-F015 — percentual da taxa de serviço, no molde de {@code pdv.sale.max-discount-percent}.
     * Configuração e não constante porque 10% é o costume do salão, não uma lei.
     */
    private final BigDecimal serviceFeePercent;

    /** PDV-F019 — validação e cotação do kit montável. Nulo só nos testes que não o exercitam. */
    private final KitBuilderUseCase kitBuilderUseCase;

    /** PDV-F021 — cardápio de sessão. Nulo só nos testes que não o exercitam. */
    private final SessionMenuService sessionMenu;

    /**
     * PDV-F021 — a sessão baseada em produto (PDV-F010) foi substituída pelo cardápio. Em produção
     * vem de {@code pdv.sessao.legacy-enabled} (padrão {@code false}); os construtores antigos a
     * mantêm ligada para os testes da sessão por produto continuarem exercitando aquele caminho.
     */
    private final boolean legacySessionEnabled;

    /**
     * PDV-F024 — a mesa passou a ser só sessões do cardápio. Em produção vem de
     * {@code pdv.mesa.catalog-items-enabled} (padrão {@code false}); os construtores antigos a mantêm
     * ligada para os testes da comanda por produto continuarem exercitando aquele caminho.
     */
    private final boolean catalogItemsEnabled;

    /** Categoria usada para resolver a taxa de cashback da linha de sessão, que não tem produto. */
    static final String SESSION_CASHBACK_CATEGORY = "Sessão";

    public ComandaService(ComandaRepository comandaRepository, EstoqueUseCase estoqueUseCase,
            OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            CashbackUseCase cashbackUseCase, PdvService pdvService,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            BigDecimal serviceFeePercent) {
        this(comandaRepository, estoqueUseCase, orderRepository, orderPaymentRepository, cashbackUseCase,
                pdvService, notificationUseCase, userRepository, serviceFeePercent, null);
    }

    public ComandaService(ComandaRepository comandaRepository, EstoqueUseCase estoqueUseCase,
            OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            CashbackUseCase cashbackUseCase, PdvService pdvService,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            BigDecimal serviceFeePercent, KitBuilderUseCase kitBuilderUseCase) {
        this(comandaRepository, estoqueUseCase, orderRepository, orderPaymentRepository, cashbackUseCase,
                pdvService, notificationUseCase, userRepository, serviceFeePercent, kitBuilderUseCase, null, true);
    }

    public ComandaService(ComandaRepository comandaRepository, EstoqueUseCase estoqueUseCase,
            OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            CashbackUseCase cashbackUseCase, PdvService pdvService,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            BigDecimal serviceFeePercent, KitBuilderUseCase kitBuilderUseCase,
            SessionMenuService sessionMenu, boolean legacySessionEnabled) {
        this(comandaRepository, estoqueUseCase, orderRepository, orderPaymentRepository, cashbackUseCase,
                pdvService, notificationUseCase, userRepository, serviceFeePercent, kitBuilderUseCase, sessionMenu,
                legacySessionEnabled, true);
    }

    public ComandaService(ComandaRepository comandaRepository, EstoqueUseCase estoqueUseCase,
            OrderRepository orderRepository, OrderPaymentRepository orderPaymentRepository,
            CashbackUseCase cashbackUseCase, PdvService pdvService,
            NotificationUseCase notificationUseCase, UserRepository userRepository,
            BigDecimal serviceFeePercent, KitBuilderUseCase kitBuilderUseCase,
            SessionMenuService sessionMenu, boolean legacySessionEnabled, boolean catalogItemsEnabled) {
        this.catalogItemsEnabled = catalogItemsEnabled;
        this.kitBuilderUseCase = kitBuilderUseCase;
        this.sessionMenu = sessionMenu;
        this.legacySessionEnabled = legacySessionEnabled;
        this.comandaRepository = comandaRepository;
        this.estoqueUseCase = estoqueUseCase;
        this.orderRepository = orderRepository;
        this.orderPaymentRepository = orderPaymentRepository;
        this.cashbackUseCase = cashbackUseCase;
        this.pdvService = pdvService;
        this.notificationUseCase = notificationUseCase;
        this.userRepository = userRepository;
        this.serviceFeePercent = serviceFeePercent == null ? BigDecimal.ZERO : serviceFeePercent;
    }

    @Override
    public BigDecimal getServiceFeePercent() {
        return serviceFeePercent;
    }

    @Override
    @Transactional
    public Comanda openComanda(Long sessionId, String tableOrCustomerLabel, Long customerId, String username) {
        // Abrir continua exigindo a PRÓPRIA sessão: a mesa nasce na gaveta de quem a abriu, e é
        // esse depósito que vai baixar estoque. O compartilhamento de PDV-F010 é sobre OPERAR mesa
        // já aberta (lançar/fechar/cancelar), não sobre criar uma no caixa alheio.
        CashRegisterSession session = pdvService.requireOwnOpenSession(sessionId, username);
        return comandaRepository.save(
                Comanda.open(sessionId, session.warehouseCode(), tableOrCustomerLabel, customerId, username));
    }

    @Override
    @Transactional
    public Comanda addItem(Long comandaId, String sku, BigDecimal quantity, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId, String notes, BigDecimal surchargeAmount, String username) {
        // PDV-C008: leitura TRAVADA. Toda decisão abaixo — inclusive o requireOpen — é tomada
        // sobre este estado, e sem a trava o atendente A decidiria sobre uma mesa que B já fechou.
        Comanda comanda = getComandaForUpdate(comandaId);
        // PDV-F010: mesa é do salão, não do operador — ver PdvService.requireOpenSession.
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        requireCatalogItemsAllowed(comanda);

        ConsumptionMode resolvedMode = mode == null ? ConsumptionMode.NORMAL : mode;
        // TROCA é cortesia por definição: não depende do cliente HTTP ter marcado o campo.
        boolean resolvedCourtesy = courtesy || resolvedMode.impliesCourtesy();

        // PDV-F021 — a linha do cardápio não tem produto: entra só por addSession/addRoshExtra.
        if (resolvedMode.isMenuSession()) {
            throw new MenuSessionNotAllowedOnItemsException(resolvedMode.name());
        }

        EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(sku);
        // PDV-F021 — sessão por produto substituída pelo cardápio: nem os modos da sessão antiga
        // nem um produto de sessão entram mais por aqui (a não ser com o legado religado).
        if (!legacySessionEnabled && (resolvedMode.isSessionMode() || saleInfo.sessionProduct())) {
            throw new LegacySessionDisabledException(sku);
        }
        // Todas as validações ANTES de qualquer escrita de estoque — mesma ordem de
        // PdvService.registerSale, e a razão é a mesma: aqui cada lançamento é seu próprio commit,
        // então um débito seguido de recusa deixaria saldo baixado sem linha na comanda.
        if (!saleInfo.availableForTable()) {
            throw new NotAvailableForTableException(sku);
        }
        if (resolvedMode.isSessionMode() && !saleInfo.sessionProduct()) {
            throw new NotASessionProductException(sku, resolvedMode.name());
        }
        // PDV-C020 — kit não pode ser sessão. Ele não tem saldo próprio: explode em componentes na
        // baixa (EST-F015), e não há como um kit ser "a lata" que o contador de EST-F027 controla.
        // O QA de 06/09/2026 achou kits oferecidos como sabor no dialog de sessão, a R$ 95 —
        // desencontro de dados que o filtro do cliente não pegava porque não olhava o tipo. O
        // frontend corrigiu o filtro; esta é a guarda que impede a combinação chegar ao estoque.
        if (resolvedMode.isSessionMode() && saleInfo.kit()) {
            throw new NotASessionProductException(sku, resolvedMode.name());
        }
        validateNotes(notes);
        validateSurcharge(surchargeAmount, resolvedMode, resolvedCourtesy);
        Long resolvedLink = resolveLinkedItem(comanda, resolvedMode, linkedItemId);

        // PDV-F011 — uma linha com nota ou acréscimo nunca é "item comum": fromCatalog resolveria
        // o preço pelo SKU e não teria onde guardar os dois campos.
        boolean plainCatalogLine = resolvedMode == ConsumptionMode.NORMAL && !resolvedCourtesy
                && notes == null && !hasSurcharge(surchargeAmount);
        ComandaItem item = plainCatalogLine
                ? ComandaItem.fromCatalog(sku, quantity, saleInfo.pricing(), saleInfo.productName())
                : ComandaItem.forSession(sku, quantity,
                        resolveUnitPrice(sku, resolvedMode, resolvedCourtesy, surchargeAmount, saleInfo),
                        saleInfo.pricing(), saleInfo.productName(), resolvedMode, resolvedCourtesy,
                        resolvedLink, notes, surchargeAmount);

        // Debita agora, não no fechamento — ver a nota de classe sobre não-atomicidade. Cortesia
        // baixa estoque igual: o cliente não paga, mas a essência saiu.
        //
        // EST-F027 — dois caminhos, e a escolha é do PRODUTO, não do modo. Essência marcada como
        // produto de sessão com sessionsPerUnit declarado consome USO de uma lata aberta; a lata
        // inteira só sai do saldo quando é aberta. Antes disto, toda sessão baixava uma lata: com
        // sessionsPerUnit = 5, o estoque sumia cinco vezes mais rápido que a realidade.
        //
        // O critério é o produto porque NORMAL também é sessão quando o SKU é a essência — foi
        // exatamente o caso medido no QA (50 → 49 numa sessão simples). Produto sem
        // sessionsPerUnit segue baixando unidade, o que torna a adoção uma escolha por item de
        // catálogo em vez de uma virada de chave para a casa inteira.
        if (saleInfo.consumesOpenPackage()) {
            OpenPackage lata = estoqueUseCase.consumeSession(sku, comanda.warehouseCode(), quantity, username);
            item = item.withPackageCounter(lata.uses(), lata.sessionsPerUnit());
        } else {
            estoqueUseCase.adjustStock(sku, comanda.warehouseCode(), MovementType.SAIDA, quantity,
                    "Comanda #" + comandaId, username);
        }

        return comandaRepository.save(comanda.withAddedItem(item));
    }

    @Override
    @Transactional
    public Comanda addKit(Long comandaId, KitSelection selection, String username) {
        // Mesmas travas e checagens de addItem, na mesma ordem: tudo validado antes da primeira
        // baixa, porque aqui cada lançamento é seu próprio commit.
        Comanda comanda = getComandaForUpdate(comandaId);
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        requireCatalogItemsAllowed(comanda);

        KitQuote quote = kitBuilderUseCase.quote(selection, KitChannel.PDV);
        String bundleId = UUID.randomUUID().toString();
        Comanda updated = comanda;
        for (KitQuote.Line line : quote.lines()) {
            EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(line.sku());
            // Sem availableForTable: esse filtro é da venda avulsa na mesa ("isto é servido
            // aqui?"), e o kit é oferta montada pelo admin, que já escolheu o que entra nele.
            ComandaItem item = ComandaItem.fromCatalog(line.sku(), BigDecimal.ONE, saleInfo.pricing(),
                            saleInfo.productName())
                    .withKit(bundleId, quote.template().id(), line.discountAmount());
            estoqueUseCase.adjustStock(line.sku(), comanda.warehouseCode(), MovementType.SAIDA, BigDecimal.ONE,
                    "Comanda #" + comandaId + " (kit " + quote.template().name() + ")", username);
            updated = updated.withAddedItem(item);
        }
        return comandaRepository.save(updated);
    }

    @Override
    @Transactional
    public Comanda removeKit(Long comandaId, String kitBundleId, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);

        List<ComandaItem> linhas = comanda.openItems().stream()
                .filter(i -> kitBundleId != null && kitBundleId.equals(i.kitBundleId()))
                .toList();
        if (linhas.isEmpty()) {
            throw new ComandaItemNotFoundException(null, comandaId);
        }
        Comanda semKit = comanda;
        for (ComandaItem linha : linhas) {
            semKit = semKit.withRemovedItem(linha.id());
            undoStock(linha, comanda.warehouseCode(), "Remoção de kit da comanda #" + comandaId, username);
        }
        return comandaRepository.save(semKit);
    }

    /**
     * O preço de cada modo. É a regra que a feature inteira gira em torno.
     *
     * <p><b>A armadilha é o open rosh:</b> a linha chega com o SKU da <i>variação</i> do sabor
     * (ex.: {@code SESS-BLUE}, preço 35), mas o valor cobrado é o {@code openRoshPrice} do produto
     * <b>pai</b> (ex.: 60). Resolver o preço pelo SKU, como em todos os outros modos, cobraria 35.
     * O SKU está ali para saber qual essência sair do estoque, não para precificar.</p>
     */
    private BigDecimal resolveUnitPrice(String sku, ConsumptionMode mode, boolean courtesy,
            BigDecimal surchargeAmount, EstoqueUseCase.CatalogSaleInfo saleInfo) {
        if (courtesy) {
            return BigDecimal.ZERO;
        }
        if (mode == ConsumptionMode.OPEN_ROSH) {
            if (saleInfo.openRoshPrice() == null || saleInfo.openRoshPrice().signum() <= 0) {
                throw new OpenRoshNotPricedException(sku);
            }
            // PDV-F011 — o acréscimo soma sobre o openRoshPrice do PAI, nunca sobre o preço da
            // variação do sabor. É a mesma armadilha do open rosh, um nível acima: quem somasse
            // sobre a variante cobraria a base errada e o erro passaria despercebido, porque o
            // total continuaria "parecendo" maior.
            return hasSurcharge(surchargeAmount)
                    ? saleInfo.openRoshPrice().add(surchargeAmount)
                    : saleInfo.openRoshPrice();
        }
        // NORMAL e SABOR_EXTRA cobram o preço da variação do sabor, como qualquer item de catálogo.
        return saleInfo.pricing().effectivePrice();
    }

    private static boolean hasSurcharge(BigDecimal surchargeAmount) {
        return surchargeAmount != null && surchargeAmount.signum() > 0;
    }

    /**
     * PDV-F011 — recusar em vez de truncar. Truncado, o operador não fica sabendo que perdeu parte
     * do registro, e o registro é justamente onde mora qual pinça saiu com aquela mesa.
     */
    private void validateNotes(String notes) {
        if (notes != null && notes.length() > ComandaItem.NOTES_MAX_LENGTH) {
            throw new NotesTooLongException(notes.length(), ComandaItem.NOTES_MAX_LENGTH);
        }
    }

    /**
     * PDV-F011 — as três recusas do acréscimo, cada uma com código próprio para a tela explicar o
     * motivo certo.
     *
     * <p>A ordem importa: cortesia é checada <b>antes</b> do modo porque {@code TROCA} é as duas
     * coisas ao mesmo tempo (cortesia e não-{@code OPEN_ROSH}), e o que o operador precisa ouvir
     * ali é "esta linha o cliente não paga", não "modo errado".</p>
     */
    private void validateSurcharge(BigDecimal surchargeAmount, ConsumptionMode mode, boolean courtesy) {
        if (surchargeAmount == null) {
            return;
        }
        if (surchargeAmount.signum() < 0) {
            throw new SurchargeInvalidException(surchargeAmount);
        }
        if (surchargeAmount.signum() == 0) {
            // Zero é o mesmo que não mandar: não vale acionar recusa nem permissão por um no-op.
            return;
        }
        if (courtesy) {
            throw new SurchargeOnCourtesyException(surchargeAmount);
        }
        if (mode != ConsumptionMode.OPEN_ROSH) {
            throw new SurchargeNotApplicableException(mode.name());
        }
    }

    /**
     * A linha de origem tem que existir <b>nesta</b> comanda — não basta o id ser válido em algum
     * lugar do banco, ou uma troca poderia se pendurar no open rosh da mesa ao lado.
     */
    private Long resolveLinkedItem(Comanda comanda, ConsumptionMode mode, Long linkedItemId) {
        if (!mode.requiresLinkedItem()) {
            return null;
        }
        if (linkedItemId == null) {
            throw new LinkedItemRequiredException(mode.name(), null, comanda.id());
        }
        ComandaItem linked = comanda.items().stream()
                .filter(i -> linkedItemId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new LinkedItemRequiredException(mode.name(), linkedItemId, comanda.id()));
        // Trocas ilimitadas são a contrapartida do valor fixo do consumo livre. Permitir troca
        // cortesia sobre uma sessão comum daria narguilé de graça.
        if (mode == ConsumptionMode.TROCA && linked.mode() != ConsumptionMode.OPEN_ROSH) {
            throw new NotAnOpenRoshException(linkedItemId, linked.mode().name());
        }
        return linkedItemId;
    }

    /**
     * PDV-F021 — lança uma sessão do cardápio: preço da faixa (+ upgrade de vaso grande), essência
     * em texto, utensílios alocados. Não toca estoque. Ver {@link ComandaUseCase#addSession}.
     */
    @Override
    @Transactional
    public Comanda addSession(Long comandaId, Long tierId, String essencia, boolean vasoGrande, String username) {
        return addSession(comandaId, AddSessionCommand.simple(tierId, essencia, vasoGrande), username);
    }

    /**
     * PDV-F024 — sessão com carvão, adicionais e, no rosh duplo, o 2º rosh já pago na mesma
     * transação. Antes o front fazia duas chamadas e, se a segunda falhasse, removia a sessão à mão.
     */
    @Override
    @Transactional
    public Comanda addSession(Long comandaId, AddSessionCommand command, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        SessionMenuService menu = requireSessionMenu();
        // PDV-F027 — sem trava de sessão ativa: a mesa pode ter vários narguilés ao mesmo tempo. O
        // limite é o que a casa tem de utensílio, conferido logo abaixo.

        SessionTier tier = menu.requireActiveTier(command.tierId());
        SessionSettings settings = menu.settings();
        String notes = sessionNotes(command.essencia(), command.vasoGrande());
        List<SessionAddon> addons = menu.requireActiveAddons(command.adicionalIds());
        SessionTier tierRosh = null;
        String notesRosh = null;
        if (command.duplo()) {
            // Validado antes de qualquer gravação: rosh duplo sem sabor não chega a criar a sessão.
            notesRosh = sessionNotes(command.essenciaRosh(), false);
            tierRosh = command.tierIdRosh() == null ? tier : menu.requireActiveTier(command.tierIdRosh());
        }
        // Trava e confere os utensílios ANTES de gravar a linha: sem vaso livre não há sessão.
        List<SessionAssetType> assets = menu.reserveAssetsForSession(settings, command.vasoGrande());

        SessionSetup setup = SessionSetup.of(command.carvao(), addons, command.vasoGrande());
        BigDecimal price = (command.vasoGrande() ? tier.preco().add(settings.upgradeVasoGrandePreco()) : tier.preco())
                .add(setup.addonsTotal());
        StringBuilder productName = new StringBuilder("Sessão ").append(tier.nome());
        if (command.vasoGrande()) {
            productName.append(" + vaso grande");
        }
        addons.forEach(a -> productName.append(" + ").append(a.nome()));
        ComandaItem item = ComandaItem.forMenuSession(tier.sku(), price, productName.toString(),
                ConsumptionMode.SESSAO, false, null, notes, SessionProgress.awaitingPayment(), setup);

        Comanda saved = comandaRepository.save(comanda.withAddedItem(item));
        Long sessionItemId = newItemId(comanda, saved);
        menu.allocate(sessionItemId, assets);
        if (!command.duplo()) {
            return saved;
        }
        // O 2º rosh usa o mesmo narguilé: nenhum utensílio novo. Cortesia a R$ 0 em qualquer dia —
        // o duplo é um modo da casa, não mais a promoção de diasDuploRosh.
        ComandaItem rosh = ComandaItem.forMenuSession(tierRosh.sku(), BigDecimal.ZERO,
                "2º rosh " + tierRosh.nome() + " (duplo rosh)", ConsumptionMode.ROSH_EXTRA, true, sessionItemId,
                notesRosh, SessionProgress.queued(), SessionSetup.of(command.carvao(), List.of()));
        return comandaRepository.save(saved.withAddedItem(rosh));
    }

    /**
     * PDV-F027 — nova sessão com a configuração de uma anterior da mesa. Monta o mesmo pedido que o
     * operador faria e delega a {@link #addSession(Long, AddSessionCommand, String)}: faixa e
     * adicionais são revalidados (inativos recusam) e o preço é o de agora, não o da origem.
     */
    @Override
    @Transactional
    public Comanda repeatSession(Long comandaId, Long sourceItemId, RepeatSessionCommand command, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        ComandaItem origem = comanda.items().stream()
                .filter(i -> sourceItemId != null && sourceItemId.equals(i.id()) && i.mode() == ConsumptionMode.SESSAO)
                .findFirst()
                .orElseThrow(() -> new NotASessionLineException(sourceItemId, comandaId));
        SessionSetup setup = origem.setup();
        boolean vasoGrande = setup != null && setup.vasoGrande();
        String essencia = command.essencia() != null && !command.essencia().isBlank()
                ? command.essencia() : origem.sessionEssencia();
        AddSessionCommand add = new AddSessionCommand(tierIdOf(origem), essencia, vasoGrande,
                setup == null ? null : setup.charcoal(),
                setup == null ? List.of() : setup.addons().stream().map(SessionSetup.Addon::addonId).toList(),
                command.duplo(), command.essenciaRosh(), command.tierIdRosh());
        return addSession(comandaId, add, username);
    }

    /**
     * PDV-F021 — 2º rosh de uma sessão: nova essência, mesmos utensílios. De graça (cortesia) no
     * primeiro rosh extra de uma sessão em dia de duplo rosh; pelo preço da faixa nos demais casos.
     * Ver {@link ComandaUseCase#addRoshExtra}.
     */
    @Override
    @Transactional
    public Comanda addRoshExtra(Long comandaId, Long sessionItemId, Long tierId, String essencia, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        SessionMenuService menu = requireSessionMenu();

        // PDV-F023 — a sessão é paga na hora, então o pai do rosh já pode estar cobrado. O que importa
        // é ele ainda estar no salão: rosh extra de sessão recolhida não tem narguilé onde ir.
        ComandaItem sessao = comanda.items().stream()
                .filter(i -> i.id() != null && i.id().equals(sessionItemId) && i.mode() == ConsumptionMode.SESSAO)
                .filter(i -> i.session() == null ? i.isOpen() : !i.session().isCollected())
                .findFirst()
                .orElseThrow(() -> new NotASessionLineException(sessionItemId, comandaId));
        SessionTier tier = menu.requireActiveTier(tierId != null ? tierId : tierIdOf(sessao));
        String notes = sessionNotes(essencia, false);

        boolean promoJaUsada = comanda.items().stream()
                .anyMatch(i -> sessionItemId.equals(i.linkedItemId()) && i.mode() == ConsumptionMode.ROSH_EXTRA
                        && i.courtesy());
        boolean promo = !promoJaUsada && menu.isDuploRoshDay(menu.settings(), comanda.openedAt());

        ComandaItem item = ComandaItem.forMenuSession(tier.sku(), promo ? BigDecimal.ZERO : tier.preco(),
                "2º rosh " + tier.nome() + (promo ? " (duplo rosh)" : ""), ConsumptionMode.ROSH_EXTRA, promo,
                sessionItemId, notes, SessionProgress.queued(), null);
        return comandaRepository.save(comanda.withAddedItem(item));
    }

    /** PDV-F024 — ver {@link #catalogItemsEnabled}. */
    private void requireCatalogItemsAllowed(Comanda comanda) {
        if (!catalogItemsEnabled) {
            throw new CatalogItemNotAllowedOnTableException(comanda.id());
        }
    }

    private SessionMenuService requireSessionMenu() {
        if (sessionMenu == null) {
            throw new IllegalStateException("cardápio de sessão não configurado neste ComandaService");
        }
        return sessionMenu;
    }

    /** Essência obrigatória (é o que a casa quer saber depois) e o vaso, na nota da linha. */
    private String sessionNotes(String essencia, boolean vasoGrande) {
        if (essencia == null || essencia.isBlank()) {
            throw new SessionEssenceRequiredException("essência");
        }
        String notes = essencia.trim() + (vasoGrande ? ComandaItem.VASO_GRANDE_NOTE_SUFFIX : "");
        validateNotes(notes);
        return notes;
    }

    /** A faixa de uma linha de sessão vem do SKU sintético {@code SESS-{id}}. */
    private static Long tierIdOf(ComandaItem sessao) {
        try {
            return Long.valueOf(sessao.sku().substring(SessionTier.SKU_PREFIX.length()));
        } catch (RuntimeException e) {
            throw new IllegalStateException("linha de sessão com SKU fora do padrão: " + sessao.sku(), e);
        }
    }

    /** Id da linha que o save acabou de gerar — a que não existia antes. */
    private static Long newItemId(Comanda before, Comanda after) {
        Set<Long> antes = before.items().stream().map(ComandaItem::id).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return after.items().stream().map(ComandaItem::id)
                .filter(id -> id != null && !antes.contains(id))
                .max(Long::compareTo)
                .orElseThrow(() -> new IllegalStateException("linha de sessão gravada sem id"));
    }

    /** PDV-F021 — devolve à casa os utensílios das linhas de sessão informadas. */
    private void releaseSessionAssets(List<ComandaItem> items) {
        if (sessionMenu == null) {
            return;
        }
        List<Long> ids = items.stream()
                .filter(i -> i.mode() == ConsumptionMode.SESSAO && i.id() != null)
                .map(ComandaItem::id)
                .toList();
        if (!ids.isEmpty()) {
            sessionMenu.release(ids);
        }
    }

    @Override
    @Transactional
    public Comanda removeItem(Long comandaId, Long itemId, String username) {
        // PDV-C008 — quarto caminho de mutação, e portanto quarta leitura travada. Sem ela,
        // remover e fechar em paralelo devolveriam ao estoque um item que o outro caminho acabou
        // de cobrar no pedido.
        Comanda comanda = getComandaForUpdate(comandaId);
        // PDV-F010: mesa compartilhada — ver PdvService.requireOpenSession. Mesmo critério de
        // addItem e cancelComanda: a sessão de ORIGEM tem que estar aberta, porque é o depósito
        // dela que recebe a devolução.
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);

        ComandaItem alvo = comanda.items().stream()
                .filter(i -> itemId != null && itemId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new ComandaItemNotFoundException(itemId, comandaId));
        // PDV-C023 — linha já cobrada pertence a um pedido pago: tirá-la da comanda liberaria o
        // utensílio de uma sessão que pode estar na mesa e, no catálogo, devolveria ao estoque
        // mercadoria vendida. Quem quer desfazer uma venda paga reembolsa o pedido.
        if (!alvo.isOpen()) {
            throw new ItemNotOpenInComandaException(comandaId, List.of(itemId));
        }
        // PDV-F019 — linha de kit sai só com o pacote inteiro.
        if (alvo.inKit()) {
            throw new KitItemRemovalNotAllowedException(itemId, alvo.kitBundleId());
        }

        // A troca sai junto (é cortesia e não existe sem a sessão); o sabor extra barra, porque
        // pode estar cobrado — ver o javadoc de Comanda.withRemovedItem.
        List<ComandaItem> cobradasPenduradas = comanda.chargedChildrenOf(itemId);
        if (!cobradasPenduradas.isEmpty()) {
            throw new LinkedItemIsChargedException(itemId,
                    cobradasPenduradas.stream().map(ComandaItem::id).toList());
        }

        // Captura ANTES de remover: depois da remoção a comanda não sabe mais quais linhas saíram,
        // e são elas que precisam ter o estoque devolvido.
        List<ComandaItem> removidas = comanda.itemsRemovedWith(itemId);
        Comanda semItem = comanda.withRemovedItem(itemId);

        // Devolve o que cada linha removida havia debitado — mesma ENTRADA de cancelComanda. A
        // cortesia também volta: ela não foi cobrada, mas a essência tinha saído do estoque.
        for (ComandaItem removida : removidas) {
            undoStock(removida, comanda.warehouseCode(), "Remoção de item da comanda #" + comandaId, username);
        }
        // PDV-F021 — sessão removida devolve os utensílios à casa.
        releaseSessionAssets(removidas);
        // Sem checagem de "última linha": comanda vazia é estado legítimo — é como ela nasce, e o
        // COMANDA_EMPTY do fechamento já barra fechá-la assim.
        return comandaRepository.save(semItem);
    }

    @Override
    @Transactional(readOnly = true)
    public Comanda getComanda(Long comandaId) {
        return comandaRepository.findById(comandaId)
                .orElseThrow(() -> new ComandaNotFoundException(comandaId));
    }

    /**
     * PDV-C008 — leitura travada para os três caminhos que mudam a comanda. A consulta de tela
     * (`GET /pdv/comandas/{id}`) continua usando {@link #getComanda}: travar linha para exibir
     * seguraria a mesa enquanto alguém apenas olha.
     */
    private Comanda getComandaForUpdate(Long comandaId) {
        return comandaRepository.findByIdForUpdate(comandaId)
                .orElseThrow(() -> new ComandaNotFoundException(comandaId));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Comanda> listOpenComandas(Long sessionId, String warehouseCode, int page, int size) {
        return comandaRepository.findOpen(sessionId, warehouseCode, page, size);
    }

    @Override
    @Transactional
    public Order closeComanda(Long comandaId, List<PaymentCommand> payments, BigDecimal discountAmount,
            boolean applyServiceFee, List<Long> itemIds, String username) {
        // PDV-C008 — ver getComandaForUpdate. É aqui que a trava mais importa: sem ela, dois
        // fechamentos concorrentes da mesma mesa gerariam dois pedidos concluídos dos mesmos itens.
        Comanda comanda = getComandaForUpdate(comandaId);
        requireOpen(comanda);
        // PDV-F017 — "vazia" passou a significar "sem linha em aberto". Uma comanda cujas linhas já
        // foram todas cobradas em fechamentos parciais não deveria nem chegar aqui (o último
        // fechamento a encerra), mas a checagem é a rede de segurança.
        if (comanda.openItems().isEmpty()) {
            throw new ComandaEmptyException(comandaId);
        }
        // O escopo deste fechamento: as linhas escolhidas, ou todas as abertas quando o cliente não
        // escolhe. Daqui para baixo NADA olha comanda.items() de novo — desconto, taxa, pagamento e
        // cashback são todos sobre o escopo, e misturar as duas leituras é o erro que faria o
        // cliente da primeira conta pagar o consumo da mesa inteira.
        List<ComandaItem> escopo = resolveClosingScope(comanda, itemIds);
        boolean partial = itemIds != null && !itemIds.isEmpty();
        // PDV-F023 — fechar sem itemIds encerra a mesa, e encerrar com narguilé no salão liberaria
        // utensílio que continua em uso. Cancelar segue permitido.
        if (!partial && comanda.hasActiveSession()) {
            throw new SessionNotCollectedException(comandaId);
        }
        // PDV-F010, decisão do dono: quando B fecha a mesa aberta por A, o pedido entra na gaveta
        // de B — o dinheiro pertence a quem o recebeu, e é a conferência de B que precisa fechar no
        // fim do turno. Também é o que impede o pedido de cair numa sessão que A já encerrou.
        // Sem sessão aberta não há como receber: getCurrentSession recusa com 409.
        CashRegisterSession receivingSession = pdvService.getCurrentSession(username);
        // Comanda só de cortesias não fecha — irmã de COMANDA_EMPTY. Pelo desenho da feature a
        // cortesia é sempre acessória de uma sessão paga, então total zero aqui é erro de
        // lançamento, e fechá-lo geraria um pedido concluído de R$ 0 que ninguém revisaria.
        if (escopo.stream().allMatch(ComandaItem::courtesy)) {
            throw new ComandaOnlyCourtesyException(comandaId);
        }

        // PDV-F014 — o desconto é pedido sobre a conta ("tira 20 reais"), mas o modelo guarda
        // desconto POR ITEM: Order.discountAmount é a soma dos itens, e é sobre o líquido de cada
        // item que o cashback é creditado e a margem calculada. Ratear é o que impede a casa de
        // pagar cashback sobre dinheiro que não recebeu e de ver margem cheia numa venda abatida.
        // A cortesia absorve zero por construção — a proporção de uma linha de valor zero é zero.
        //
        // PDV-F019 — a base do rateio é o LÍQUIDO do desconto de kit: a linha de kit já vem abatida,
        // e ratear sobre o bruto poderia dar a ela mais desconto total que o valor dela.
        List<BigDecimal> lineAmounts = escopo.stream().map(ComandaItem::netSubtotal).toList();
        // PDV-C016 — desconto maior que a conta é recusado AQUI, com código próprio. distribute()
        // já recusava (não há como ratear um abatimento maior que a soma das linhas sem violar a
        // invariante de OrderItem), mas com IllegalArgumentException, que o handler global achata
        // num 400 genérico. E o problema maior é a ORDEM: o rateio roda ANTES de
        // requireDiscountWithinLimit, então o desconto absurdo nunca chegava ao
        // 409 DISCOUNT_LIMIT_EXCEEDED que a tela já trata — pedir 11% dava um erro acionável,
        // pedir o dobro da conta dava "Requisição inválida". A checagem fica no service e não
        // dentro de distribute: aquela é função pura de aritmética, e o vocabulário de erro do PDV
        // não é dela.
        if (discountAmount != null && discountAmount.signum() > 0) {
            BigDecimal billAmount = lineAmounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (discountAmount.compareTo(billAmount) > 0) {
                throw new DiscountExceedsBillException(comandaId, discountAmount, billAmount);
            }
        }
        List<BigDecimal> lineDiscounts = DiscountProration.distribute(lineAmounts, discountAmount);

        // Cada ComandaItem já tem preço e custo congelados no lançamento — vira OrderItem por
        // reconstituição (of), NUNCA por fromCatalog de novo: reprecificar aqui repreçaria em
        // silêncio itens que o cliente já consumiu, se o catálogo mudou nas horas em que a
        // comanda ficou aberta. Com o open rosh isso ficou ainda mais crítico: fromCatalog
        // resolveria pelo SKU da variação e cobraria o preço do sabor no lugar do valor fixo.
        List<OrderItem> orderItems = new ArrayList<>(escopo.size());
        for (int i = 0; i < escopo.size(); i++) {
            ComandaItem item = escopo.get(i);
            // CRM-F003, mesma regra do balcão (PdvService.registerSale): a taxa vigente é resolvida
            // e CARIMBADA no pedido, para mudar a taxa amanhã não reescrever o cashback de hoje.
            // Sem isto o cliente vinculado na abertura chega ao pedido e não ganha nada — o
            // recordEarnedForOrder lá embaixo é chamado, mas não acha item nenhum com valor a
            // lançar. Cortesia não precisa de exceção: o ganho é sobre o líquido, e o líquido dela
            // é zero por construção.
            // PDV-F021 — sessão do cardápio não tem produto: a taxa sai da categoria "Sessão" (ou da
            // global), sem ir ao catálogo, que não conhece o SKU sintético.
            CashbackRate rate = item.mode().isCatalogLine()
                    ? cashbackUseCase.resolveApplicableRate(item.sku())
                    : cashbackUseCase.resolveApplicableRate(item.sku(), SESSION_CASHBACK_CATEGORY);
            // PDV-F011: notes e surchargeAmount atravessam junto com mode/courtesy. A nota porque
            // "qual pinça saiu com aquela mesa" é pergunta feita DEPOIS do fechamento; o acréscimo
            // porque não dá para reconstruí-lo do unitPrice, que já é a soma.
            orderItems.add(OrderItem.of(null, item.sku(), item.quantity(), item.unitPrice(), item.costPrice(),
                    lineDiscounts.get(i).add(item.kitDiscount()), rate == null ? null : rate.percent(),
                    item.productName(), item.mode(),
                    item.courtesy(), item.notes(), item.surchargeAmount(),
                    // PDV-F024 — o carvão atravessa como o notes: só registro.
                    item.setup() == null || item.setup().charcoal() == null ? null
                            : item.setup().charcoal().name()));
        }

        // O canal é imutável: o pedido da mesa precisa NASCER MESA, não virar depois. O depósito
        // continua sendo o da comanda (é de lá que o estoque saiu, item a item), mesmo quando quem
        // fecha é de outra gaveta.
        Order order = Order.openMesa(receivingSession.id(), comanda.warehouseCode(), comanda.customerId(),
                comandaId, comanda.tableOrCustomerLabel(), orderItems);
        // Mesmo teto do balcão, e de propósito: o limite é política comercial da casa, não
        // característica do canal. Checado DEPOIS de montar o pedido porque a regra é percentual
        // sobre o bruto, e é o pedido que sabe o bruto.
        // PDV-F019 — desconto de kit montável não conta para o teto (ver PdvService).
        BigDecimal kitDiscountTotal = escopo.stream().map(ComandaItem::kitDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (kitDiscountTotal.signum() > 0) {
            pdvService.requireDiscountWithinLimit(order, kitDiscountTotal);
        } else {
            pdvService.requireDiscountWithinLimit(order);
        }

        // PDV-F015 — a taxa entra por último, sobre o LÍQUIDO: os 10% incidem sobre o que o cliente
        // de fato vai pagar pela mercadoria, não sobre o valor antes do abatimento. Cobrar serviço
        // sobre um desconto que a casa acabou de conceder seria devolver parte dele com a outra mão.
        // PDV-F023 — a sessão de narguilé nunca leva taxa: a base são só as linhas de catálogo.
        if (applyServiceFee) {
            order = order.withServiceFeeOnCatalogLines(serviceFeePercent);
        }

        // Valida contra totalPayable, não contra netAmount: o cliente paga a mercadoria MAIS a
        // taxa, e o troco sai dessa conta. É o único lugar do módulo em que os dois números diferem.
        BigDecimal changeAmount = pdvService.validatePaymentsAndComputeChange(payments, order.totalPayable());
        // CRM-F010 — marcar na mesa segue a regra do balcão: VIP, prazo, limite, sem vencido. A taxa
        // de serviço e o desconto já incidiram — o MARCADO cobre o que sobrou a pagar.
        pdvService.validateOnAccount(comanda.customerId(), payments);

        // Sem novo adjustStock aqui: o estoque já saiu item a item em addItem.
        Order saved = orderRepository.save(
                order.concluded(orderRepository.nextOrderNumber(), changeAmount, Instant.now()));
        for (PaymentCommand payment : payments) {
            orderPaymentRepository.save(PdvService.toPaymentLine(saved.id(), payment));
        }
        pdvService.recordReceivableIfOnAccount(saved, comandaId, payments, username);
        cashbackUseCase.recordEarnedForOrder(saved);

        // PDV-F017 — marca as linhas cobradas e só ENCERRA a mesa quando não sobra nenhuma aberta.
        // Enquanto sobra, a comanda continua ABERTA com order_id nulo, que é exatamente o que o
        // ck_comanda_status_consistency da V104 exige — por isso a conta dividida não precisou de
        // status novo nem de migration no cabeçalho.
        List<Long> escopoIds = escopo.stream().map(ComandaItem::id).toList();
        Comanda cobrada = comanda.withItemsClosedIn(saved.id(), escopoIds);
        // PDV-F023 — fechamento parcial NUNCA encerra a mesa nem libera utensílio, mesmo levando a
        // última linha aberta: com a sessão paga no lançamento, toda sessão é a última linha aberta,
        // e a mesa fecharia com o narguilé ainda nela. Quem libera é o RECOLHIDO; quem encerra é o
        // close sem itemIds ou o finish.
        if (partial) {
            // PDV-F027 — a sessão paga agora sai de AGUARDANDO_PAGAMENTO e vai ao preparo.
            comandaRepository.save(cobrada.withSessionsPaid(escopoIds, Instant.now()));
            return saved;
        }
        releaseSessionAssets(escopo);
        comandaRepository.save(cobrada.closed(saved.id(), Instant.now()));
        comandaRepository.recordClosing(comandaId, username, null);
        return saved;
    }

    /**
     * As linhas que este fechamento cobra (PDV-F017): as escolhidas, ou todas as abertas quando
     * {@code itemIds} vem nulo/vazio — o caminho de sempre, que precisa continuar idêntico.
     *
     * <p>A validação de vínculo é o que impede a conta dividida de partir um consumo ao meio: um
     * {@code OPEN_ROSH} e as {@code TROCA}/{@code SABOR_EXTRA} pendurados nele saem juntos ou não
     * saem. Checa nos dois sentidos porque as duas seleções erradas são igualmente fáceis de fazer
     * na tela — marcar a troca sem a sessão, ou a sessão sem a troca.</p>
     */
    private List<ComandaItem> resolveClosingScope(Comanda comanda, List<Long> itemIds) {
        List<ComandaItem> abertas = comanda.openItems();
        if (itemIds == null || itemIds.isEmpty()) {
            return abertas;
        }
        Set<Long> escolhidos = new LinkedHashSet<>(itemIds);
        Set<Long> abertosIds = abertas.stream().map(ComandaItem::id).collect(Collectors.toSet());
        List<Long> invalidos = escolhidos.stream().filter(id -> !abertosIds.contains(id)).toList();
        if (!invalidos.isEmpty()) {
            throw new ItemNotOpenInComandaException(comanda.id(), invalidos);
        }

        List<Long> faltando = new ArrayList<>();
        for (ComandaItem item : abertas) {
            boolean dentro = escolhidos.contains(item.id());
            // Filha dentro exige o pai dentro — se o pai ainda estiver aberto. PDV-F023: o rosh extra
            // lançado depois de a sessão ter sido paga tem o pai já cobrado, e cobra sozinho.
            if (dentro && item.linkedItemId() != null && !escolhidos.contains(item.linkedItemId())
                    && abertosIds.contains(item.linkedItemId())) {
                faltando.add(item.linkedItemId());
            }
            // Pai dentro exige toda filha ainda aberta dentro.
            if (!dentro && item.linkedItemId() != null && escolhidos.contains(item.linkedItemId())) {
                faltando.add(item.id());
            }
        }
        if (!faltando.isEmpty()) {
            throw new LinkedItemMustCloseTogetherException(comanda.id(), faltando.stream().distinct().toList());
        }
        return abertas.stream().filter(i -> escolhidos.contains(i.id())).toList();
    }

    /** PDV-F023 — encerra a mesa já toda paga. Ver {@link ComandaUseCase#finishComanda}. */
    @Override
    @Transactional
    public Comanda finishComanda(Long comandaId, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        requireOpen(comanda);
        if (comanda.items().isEmpty()) {
            // Mesa sem consumo nenhum não tem pedido a pendurar no cabeçalho — a saída é cancelar.
            throw new ComandaEmptyException(comandaId);
        }
        if (!comanda.openItems().isEmpty()) {
            throw new ComandaHasOpenItemsException(comandaId);
        }
        if (comanda.hasActiveSession()) {
            throw new SessionNotCollectedException(comandaId);
        }
        Long lastOrderId = comanda.lastChargedOrderId()
                .orElseThrow(() -> new IllegalStateException("mesa " + comandaId + " paga sem pedido"));
        // Tudo recolhido já liberou os utensílios; a chamada cobre linha anterior à V132.
        releaseSessionAssets(comanda.items());
        Comanda finished = comandaRepository.save(comanda.closed(lastOrderId, Instant.now()));
        comandaRepository.recordClosing(comandaId, username, null);
        return finished;
    }

    /**
     * PDV-F023 — avança o status de uma sessão. Ver {@link ComandaUseCase#updateSessionStatus}.
     *
     * <p>Ao recolher, os utensílios só voltam quando o <b>grupo</b> inteiro (a sessão e os roshs
     * ligados a ela) está recolhido: o 2º rosh usa o mesmo narguilé. E a próxima linha da fila do
     * mesmo grupo é promovida, começando o tempo de mesa dela (PDV-F027: fila por narguilé).</p>
     */
    @Override
    @Transactional
    public Comanda updateSessionStatus(Long comandaId, Long itemId, SessionStatus status, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        requireOpen(comanda);
        ComandaItem item = comanda.items().stream()
                .filter(i -> itemId != null && itemId.equals(i.id()))
                .findFirst()
                .orElseThrow(() -> new ComandaItemNotFoundException(itemId, comandaId));
        if (item.session() == null) {
            throw new NotASessionLineException(itemId, comandaId);
        }
        if (!item.session().status().canTransitionTo(status)) {
            throw new InvalidSessionTransitionException(itemId, item.session().status(), status);
        }
        Instant now = Instant.now();
        Comanda updated = comanda.withSessionStatus(itemId, status, now);

        if (status == SessionStatus.RECOLHIDO) {
            Long rootId = item.mode() == ConsumptionMode.SESSAO ? item.id() : item.linkedItemId();
            boolean grupoRecolhido = updated.items().stream()
                    .filter(i -> i.id().equals(rootId) || rootId.equals(i.linkedItemId()))
                    .filter(i -> i.mode().isMenuSession())
                    .noneMatch(ComandaItem::isActiveSession);
            if (grupoRecolhido) {
                releaseSessionAssets(updated.items().stream().filter(i -> i.id().equals(rootId)).toList());
            }
            Comanda atual = updated;
            updated = updated.nextQueuedSessionOf(rootId)
                    .map(next -> atual.withSessionStatus(next.id(), SessionStatus.PREPARANDO, now))
                    .orElse(updated);
        }
        return comandaRepository.save(updated);
    }

    @Override
    @Transactional
    public Comanda cancelComanda(Long comandaId, String username) {
        return cancelComanda(comandaId, username, null);
    }

    @Override
    @Transactional
    public Comanda cancelComanda(Long comandaId, String username, String reason) {
        // PDV-C008 — ver getComandaForUpdate. Sem a trava, cancelar e fechar em paralelo
        // devolveriam o estoque de itens que o outro caminho acabou de cobrar.
        Comanda comanda = getComandaForUpdate(comandaId);
        // PDV-F010: mesa compartilhada — ver PdvService.requireOpenSession.
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        // PDV-C021 — com linha cobrada, cancelar mentiria nas duas pontas: devolveria ao estoque
        // mercadoria vendida e encerraria como abandono uma mesa com pedido pago (que sairia do
        // histórico e dos indicadores, que contam só FECHADA). Com a sessão paga no lançamento
        // (PDV-F027) isso é o normal do salão. A saída é remover as linhas abertas e encerrar.
        if (comanda.items().stream().anyMatch(i -> !i.isOpen())) {
            throw new ComandaPartiallyClosedException(comandaId);
        }

        // Devolve cada item já debitado — mesmo padrão de OrderService.refundOrder.
        for (ComandaItem item : comanda.items()) {
            undoStock(item, comanda.warehouseCode(), "Cancelamento de comanda #" + comandaId, username);
        }
        releaseSessionAssets(comanda.items());
        Comanda cancelled = comandaRepository.save(comanda.cancelled(Instant.now()));
        comandaRepository.recordClosing(comandaId, username,
                reason == null || reason.isBlank() ? null : reason.trim());
        return cancelled;
    }

    /**
     * Desfaz a baixa de estoque de uma linha — remoção de item e cancelamento de comanda.
     *
     * <p><b>Linha de lata NÃO devolve unidade</b> (EST-F027), e essa assimetria é o desenho, não um
     * caso esquecido: a essência já foi queimada e não voltou para a prateleira. Uma {@code ENTRADA}
     * aqui inventaria saldo — trocaria um erro visível (a mesa cancelada) por um invisível (o saldo
     * mentindo para cima, que só apareceria no próximo balanço sem ninguém saber de onde veio). O
     * que se desfaz é a contagem de usos.</p>
     *
     * <p>Quem decide é a própria linha, por {@code consumedPackage()}, e não uma releitura do
     * catálogo: o produto pode ter deixado de ser vendido por sessão desde o lançamento, e o
     * desfazimento tem que espelhar o que de fato aconteceu, não o cadastro de hoje.</p>
     */
    private void undoStock(ComandaItem item, String warehouseCode, String reason, String username) {
        // PDV-F021 — linha do cardápio de sessão nunca baixou estoque; não há o que devolver.
        if (!item.mode().isCatalogLine()) {
            return;
        }
        if (item.consumedPackage()) {
            estoqueUseCase.releaseSession(item.sku(), warehouseCode, item.quantity());
        } else {
            estoqueUseCase.adjustStock(item.sku(), warehouseCode, MovementType.ENTRADA,
                    item.quantity(), reason, username);
        }
    }

    /** PDV-F016 — trocar de mesa é renomear. Ver {@link ComandaUseCase#renameComanda}. */
    @Override
    @Transactional
    public Comanda renameComanda(Long comandaId, String newLabel, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        return comandaRepository.save(comanda.withLabel(newLabel));
    }

    /** PDV-F020 — ver {@link ComandaUseCase#linkCustomer}. */
    @Override
    @Transactional
    public Comanda linkCustomer(Long comandaId, Long customerId, String username) {
        Comanda comanda = getComandaForUpdate(comandaId);
        pdvService.requireOpenSession(comanda.sessionId());
        requireOpen(comanda);
        return comandaRepository.save(comanda.withCustomer(customerId));
    }

    /**
     * PDV-F016 — juntar duas mesas. Ver {@link ComandaUseCase#mergeComanda} para o desenho.
     *
     * <p>As duas comandas são travadas <b>em ordem crescente de id</b>, e não na ordem em que o
     * cliente as mandou. Duas junções simultâneas em sentidos opostos (A→B e B→A) travariam uma a
     * linha que a outra espera — o deadlock clássico de duas travas sem ordem canônica. Ordenar por
     * id elimina o ciclo por construção.</p>
     */
    @Override
    @Transactional
    public Comanda mergeComanda(Long fromComandaId, Long toComandaId, String username) {
        if (fromComandaId == null || fromComandaId.equals(toComandaId)) {
            throw new ComandaMergeNotAllowedException("Origem e destino precisam ser comandas distintas.");
        }
        // Ordem canônica das travas — ver o javadoc acima. As comandas são lidas JÁ travadas: a
        // decisão de mesclar é tomada sobre o estado que a trava garante que ninguém muda.
        Comanda origem;
        Comanda destino;
        if (fromComandaId < toComandaId) {
            origem = getComandaForUpdate(fromComandaId);
            destino = getComandaForUpdate(toComandaId);
        } else {
            destino = getComandaForUpdate(toComandaId);
            origem = getComandaForUpdate(fromComandaId);
        }
        requireOpen(origem);
        requireOpen(destino);

        if (!origem.warehouseCode().equals(destino.warehouseCode())) {
            throw new ComandaMergeNotAllowedException("As mesas são de depósitos diferentes: "
                    + origem.warehouseCode() + " e " + destino.warehouseCode()
                    + ". O estoque de cada linha saiu do depósito da comanda que a recebeu.");
        }
        // PDV-F031 — com a sessão paga no lançamento (PDV-F027), recusar origem com linha cobrada
        // tornava quase toda mesa de narguilé impossível de juntar. Vão as linhas em aberto e o
        // grupo inteiro de toda sessão ainda no salão, paga ou não (ver itemIdsToMoveOnMerge); a
        // linha cobrada fica onde está, e o pedido dela continua apontando para esta comanda.
        // Decidido ANTES do move: depois dele, a origem lida aqui está velha.
        List<Long> aMover = origem.itemIdsToMoveOnMerge();
        Optional<Long> pedidoDaOrigem = origem.lastChargedOrderId();

        // Reatribuição por FK, preservando ids — é o que mantém linkedItemId e a alocação de
        // utensílio (por comanda_item_id) válidos. Ver o javadoc de ComandaRepository.moveItems.
        comandaRepository.moveItems(fromComandaId, toComandaId, aMover);

        // PDV-C022 — RELER a origem. O objeto lido acima ainda contém as linhas movidas, e o save
        // dele as reinseria como linhas novas na origem (o move limpou o contexto de persistência,
        // e o save não acha par para elas): toda junção deixava cópias na mesa encerrada.
        Comanda origemAtual = getComandaForUpdate(fromComandaId);

        // Sem adjustStock: a mercadoria não voltou para a prateleira, mudou de conta. Com linha já
        // cobrada a origem termina FECHADA no último pedido dela — é receita real, e o histórico e
        // os indicadores só contam FECHADA. Sem nenhuma, CANCELADA, o único estado terminal sem
        // pedido que o ck_comanda_status_consistency da V104 aceita; o que distingue este
        // cancelamento de um abandono é o motivo gravado e o evento COMANDA_MERGED na trilha.
        Instant agora = Instant.now();
        comandaRepository.save(pedidoDaOrigem.isPresent()
                ? origemAtual.closed(pedidoDaOrigem.get(), agora)
                : origemAtual.cancelled(agora));
        comandaRepository.recordClosing(fromComandaId, username, "Juntada à comanda #" + toComandaId);
        return getComanda(toComandaId);
    }

    /**
     * PDV-F013 — a varredura de mesa esquecida. Ver o javadoc de
     * {@link ComandaUseCase#sweepStaleComandas} para o porquê de só a comanda <b>vazia</b> ser
     * cancelada, e de esta ser a única operação de comanda que não exige caixa aberto.
     */
    @Override
    @Transactional
    public StaleComandaSweepResult sweepStaleComandas(int staleHours, int batchSize) {
        Instant cutoff = Instant.now().minus(staleHours, ChronoUnit.HOURS);
        List<Long> candidates = comandaRepository.findOpenIdsOlderThan(cutoff, batchSize);

        int cancelled = 0;
        List<Comanda> withConsumption = new ArrayList<>();
        for (Long id : candidates) {
            // Mesma trava de addItem/close/cancel (PDV-C008). Sem ela a varredura poderia cancelar
            // uma mesa no instante em que um atendente lança o primeiro item nela — e o item cairia
            // numa comanda já CANCELADA, com o estoque debitado e ninguém para cobrar. Com a trava o
            // addItem espera e depois falha com ComandaNotOpenException: o operador VÊ o erro.
            Comanda comanda = comandaRepository.findByIdForUpdate(id).orElse(null);
            // Entre a consulta e a trava a mesa pode ter sido fechada ou cancelada por alguém.
            if (comanda == null || !comanda.isOpen()) {
                continue;
            }
            if (comanda.items().isEmpty()) {
                // Sem adjustStock: não há item, logo não há nada que tenha saído do estoque.
                comandaRepository.save(comanda.cancelled(Instant.now()));
                comandaRepository.recordClosing(id, "system", "Mesa vazia esquecida (varredura automática)");
                cancelled++;
            } else {
                withConsumption.add(comanda);
            }
        }

        if (!withConsumption.isEmpty()) {
            dispatchStaleComandaAlerts(withConsumption, staleHours);
        }
        return new StaleComandaSweepResult(cancelled, withConsumption.size());
    }

    /**
     * Uma notificação por destinatário listando <b>todas</b> as mesas, não uma por mesa — mesmo
     * princípio de {@code EstoqueService.notifyIfBelowReorderPoint}: dez mesas esquecidas numa noite
     * são um aviso, não dez.
     */
    private void dispatchStaleComandaAlerts(List<Comanda> comandas, int staleHours) {
        String title = "Mesa aberta há mais de " + staleHours + "h";
        StringBuilder body = new StringBuilder(comandas.size() == 1
                ? "A mesa a seguir está aberta com consumo e não foi fechada:"
                : comandas.size() + " mesas estão abertas com consumo e não foram fechadas:");
        comandas.forEach(c -> body.append("\n- ").append(c.tableOrCustomerLabel())
                .append(" (comanda #").append(c.id()).append("): ")
                .append(c.items().size()).append(" item(ns), total ").append(c.runningTotal())
                .append(", aberta em ").append(c.openedAt()));
        // O estoque destas NÃO foi devolvido de propósito: a essência foi consumida. Quem receber o
        // aviso decide entre cobrar, fechar como perda ou cancelar assumindo a devolução.
        body.append("\n\nO estoque destas mesas continua debitado — a varredura não devolve saldo de "
                + "consumo real. Feche ou cancele cada uma pelo PDV.");

        userRepository.findUsernamesByPermission(COMANDA_MANAGE_PERMISSION)
                .forEach(username -> notificationUseCase.notify(username, NotificationType.SYSTEM,
                        title, body.toString()));
    }

    private void requireOpen(Comanda comanda) {
        if (comanda.status() != ComandaStatus.ABERTA) {
            throw new ComandaNotOpenException(comanda.id(), comanda.status());
        }
    }

    // ── PDV-F029: histórico e indicadores de mesas ──────────────────────────────────────────

    private static final java.time.ZoneId ZONA_LOJA = java.time.ZoneId.of("America/Sao_Paulo");
    private static final int MAX_ANALYTICS_DAYS = 366;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ComandaHistoryEntry> listHistory(ComandaHistoryFilter filter, int page, int size) {
        PageResult<ClosedComanda> result = comandaRepository.findHistory(filter, page, size);
        Map<Long, List<Order>> ordersByComanda = ordersByComanda(result.content());
        List<ComandaHistoryEntry> content = result.content().stream()
                .map(c -> historyEntry(c, ordersByComanda.getOrDefault(c.comanda().id(), List.of()), Map.of()))
                .toList();
        return new PageResult<>(content, result.page(), result.size(), result.totalElements(), result.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public ComandaHistoryEntry getHistoryEntry(Long comandaId) {
        ClosedComanda closed = comandaRepository.findWithClosing(comandaId)
                .orElseThrow(() -> new ComandaNotFoundException(comandaId));
        List<Order> orders = ordersByComanda(List.of(closed)).getOrDefault(comandaId, List.of());
        Map<Long, List<OrderPayment>> payments = new LinkedHashMap<>();
        for (Order order : orders) {
            payments.put(order.id(), orderPaymentRepository.findByOrderId(order.id()));
        }
        return historyEntry(closed, orders, payments);
    }

    @Override
    @Transactional(readOnly = true)
    public ComandaAnalytics analytics(Instant from, Instant to, String warehouseCode) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new InvalidReportPeriodException("Informe 'from' e 'to', com 'from' antes de 'to'");
        }
        if (Duration.between(from, to).toDays() > MAX_ANALYTICS_DAYS) {
            throw new InvalidReportPeriodException("Intervalo máximo permitido: " + MAX_ANALYTICS_DAYS + " dias");
        }
        List<ClosedComanda> closed = comandaRepository.findClosedBetween(from, to, warehouseCode);
        Map<Long, List<Order>> ordersByComanda = ordersByComanda(closed);
        List<ComandaHistoryEntry> entries = closed.stream()
                .map(c -> historyEntry(c, ordersByComanda.getOrDefault(c.comanda().id(), List.of()), Map.of()))
                .toList();

        BigDecimal receita = sum(entries, ComandaHistoryEntry::totalPaid);
        int sessoes = 0;
        BigDecimal receitaSessoes = BigDecimal.ZERO;
        for (ComandaHistoryEntry e : entries) {
            for (Order order : e.orders()) {
                if (order.status() == OrderStatus.REEMBOLSADO) {
                    continue;
                }
                for (OrderItem item : order.items()) {
                    if (item.mode() == ConsumptionMode.SESSAO) {
                        sessoes++;
                        receitaSessoes = receitaSessoes.add(item.netAmount());
                    }
                }
            }
        }

        Map<String, List<ComandaHistoryEntry>> byAttendant = new TreeMap<>();
        Map<String, List<ComandaHistoryEntry>> byTable = new TreeMap<>();
        Map<String, String> tableDisplay = new HashMap<>();
        Map<Integer, List<ComandaHistoryEntry>> byHour = new TreeMap<>();
        for (ComandaHistoryEntry e : entries) {
            byAttendant.computeIfAbsent(e.comanda().openedBy(), k -> new ArrayList<>()).add(e);
            String label = e.comanda().tableOrCustomerLabel().trim();
            String key = label.toLowerCase(java.util.Locale.ROOT);
            tableDisplay.putIfAbsent(key, label);
            byTable.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            int hour = e.comanda().openedAt().atZone(ZONA_LOJA).getHour();
            byHour.computeIfAbsent(hour, k -> new ArrayList<>()).add(e);
        }

        List<PorAtendente> porAtendente = byAttendant.entrySet().stream()
                .map(en -> new PorAtendente(en.getKey(), en.getValue().size(),
                        sum(en.getValue(), ComandaHistoryEntry::totalPaid)))
                .sorted(Comparator.comparing(PorAtendente::receita).reversed())
                .toList();
        List<PorMesa> porMesa = byTable.entrySet().stream()
                .map(en -> new PorMesa(tableDisplay.get(en.getKey()), en.getValue().size(),
                        sum(en.getValue(), ComandaHistoryEntry::totalPaid), averageMinutes(en.getValue())))
                .sorted(Comparator.comparing(PorMesa::receita).reversed())
                .toList();
        List<PorHora> porHora = byHour.entrySet().stream()
                .map(en -> new PorHora(en.getKey(), en.getValue().size(),
                        sum(en.getValue(), ComandaHistoryEntry::totalPaid)))
                .toList();

        BigDecimal ticket = entries.isEmpty() ? BigDecimal.ZERO
                : receita.divide(BigDecimal.valueOf(entries.size()), 2, java.math.RoundingMode.HALF_UP);
        return new ComandaAnalytics(entries.size(), ticket, averageMinutes(entries), receita,
                sum(entries, ComandaHistoryEntry::serviceFeeTotal), sum(entries, ComandaHistoryEntry::discountTotal),
                new SessoesNarguile(sessoes, receitaSessoes), porAtendente, porMesa, porHora);
    }

    private Map<Long, List<Order>> ordersByComanda(List<ClosedComanda> comandas) {
        if (comandas.isEmpty()) {
            return Map.of();
        }
        return orderRepository.findByComandaIds(comandas.stream().map(c -> c.comanda().id()).toList()).stream()
                .collect(Collectors.groupingBy(Order::comandaId, LinkedHashMap::new, Collectors.toList()));
    }

    private static ComandaHistoryEntry historyEntry(ClosedComanda closed, List<Order> orders,
            Map<Long, List<OrderPayment>> payments) {
        Comanda comanda = closed.comanda();
        Long duration = comanda.closedAt() == null ? null
                : Duration.between(comanda.openedAt(), comanda.closedAt()).toMinutes();
        List<Order> paid = orders.stream().filter(o -> o.status() != OrderStatus.REEMBOLSADO).toList();
        // A cortesia é gravada com preço zero (ck_comanda_item_courtesy_is_free) e o preço de venda
        // não fica em lugar nenhum: o que a casa deu é medido pelo custo congelado na linha.
        BigDecimal courtesy = BigDecimal.ZERO;
        for (Order order : paid) {
            for (OrderItem item : order.items()) {
                if (item.courtesy() && item.costPrice() != null) {
                    courtesy = courtesy.add(item.quantity().multiply(item.costPrice()));
                }
            }
        }
        courtesy = courtesy.setScale(2, java.math.RoundingMode.HALF_UP);
        int sessions = (int) comanda.items().stream().filter(i -> i.mode() == ConsumptionMode.SESSAO).count();
        return new ComandaHistoryEntry(comanda, closed.closedBy(), closed.cancelReason(), orders, payments, duration,
                paid.stream().map(Order::totalPayable).reduce(BigDecimal.ZERO, BigDecimal::add),
                paid.stream().map(Order::serviceFeeAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                paid.stream().map(Order::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                courtesy, sessions);
    }

    private static BigDecimal sum(List<ComandaHistoryEntry> entries,
            java.util.function.Function<ComandaHistoryEntry, BigDecimal> field) {
        return entries.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long averageMinutes(List<ComandaHistoryEntry> entries) {
        return Math.round(entries.stream().map(ComandaHistoryEntry::durationMinutes)
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).average().orElse(0));
    }
}
