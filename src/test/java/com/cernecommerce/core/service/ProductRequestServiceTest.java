package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InvalidProductRequestTransitionException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestSkuNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestValidationException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductRequest;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntry;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntryStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEvent;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEventType;
import com.cernecommerce.core.domain.model.estoque.ProductRequestNotifyChannel;
import com.cernecommerce.core.domain.model.estoque.ProductRequestSort;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStats;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.ports.in.ProductRequestUseCase.CreateCommand;
import com.cernecommerce.core.ports.in.ProductRequestUseCase.Detail;
import com.cernecommerce.core.ports.in.ProductRequestUseCase.EntryCommand;
import com.cernecommerce.core.ports.in.ProductRequestUseCase.NotifyResult;
import com.cernecommerce.core.ports.in.ProductRequestUseCase.StatusCommand;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.crm.AutomationDispatchQueue;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRequestRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T12:00:00Z");

    private final InMemoryRepository repository = new InMemoryRepository();
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final CustomerRepository customerRepository = mock(CustomerRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CampaignAutomationRepository automationRepository = mock(CampaignAutomationRepository.class);
    private final AutomationDispatchQueue queue = mock(AutomationDispatchQueue.class);
    /** Sem transação nos testes: despacha na hora, como o executor real faz fora de transação. */
    private final AfterCommitExecutor afterCommit = new AfterCommitExecutor() {
        @Override
        public <T> void accumulate(String key, T item, Consumer<List<T>> flush) {
            flush.accept(List.of(item));
        }
    };

    private ProductRequestService service;
    private final Map<Long, Customer> customers = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new ProductRequestService(repository, productRepository, categoryRepository, customerRepository,
                userRepository, automationRepository, queue, afterCommit, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        lenient().when(customerRepository.findById(any())).thenAnswer(inv ->
                Optional.ofNullable(customers.get((Long) inv.getArgument(0))));
        lenient().when(customerRepository.findByIds(anyCollection())).thenAnswer(inv -> {
            Collection<Long> ids = inv.getArgument(0);
            return ids.stream().map(customers::get).filter(java.util.Objects::nonNull).toList();
        });
        lenient().when(productRepository.findByAnySku(anyString())).thenReturn(Optional.empty());
        lenient().when(automationRepository.findActiveByEvent(any())).thenReturn(List.of());
        customers.put(7L, Customer.of(7L, "Maria Souza", "85999990007", null, null, "balcao", NOW,
                CustomerStage.CLIENTE_ATIVO));
        customers.put(8L, Customer.of(8L, "Pedro", "85999990008", null, null, "balcao", NOW,
                CustomerStage.CLIENTE_ATIVO));
    }

    private static EntryCommand crm(long customerId, int qty) {
        return new EntryCommand(customerId, null, null, qty, null, null, null, null);
    }

    private static EntryCommand avulso(String phone, int qty) {
        return new EntryCommand(null, "João", phone, qty, null, null, null, null);
    }

    private Detail novo(EntryCommand entry) {
        return service.create(new CreateCommand("Essência Ziggy Love 66", "Ziggy", "50g", null, null, entry), "ana");
    }

    private void comAutomacaoAtiva() {
        CampaignAutomation automation = mock(CampaignAutomation.class);
        when(automation.acceptsStage(any())).thenReturn(true);
        when(automationRepository.findActiveByEvent(AutomationEvent.PEDIDO_PRODUTO_CHEGOU))
                .thenReturn(List.of(automation));
    }

    @Test
    void cria_item_com_a_primeira_solicitacao_e_historico() {
        Detail d = novo(crm(7L, 2));

        assertThat(d.view().request().status()).isEqualTo(ProductRequestStatus.PENDING);
        assertThat(d.view().stats().entriesCount()).isEqualTo(1);
        assertThat(d.view().stats().totalQuantity()).isEqualTo(2);
        assertThat(d.entries()).singleElement().satisfies(e -> {
            assertThat(e.customerName()).isEqualTo("Maria Souza");
            assertThat(e.customerPhone()).isEqualTo("85999990007");
        });
        assertThat(d.events()).extracting(ev -> ev.event().type()).containsExactly(ProductRequestEventType.CREATED);
    }

    @Test
    void mesmo_cliente_soma_quantidade_sem_duplicar() {
        Long id = novo(crm(7L, 2)).view().request().id();

        Detail d = service.addEntry(id, crm(7L, 3), "bia");

        assertThat(d.entries()).hasSize(1);
        assertThat(d.view().stats().totalQuantity()).isEqualTo(5);
        assertThat(d.events().get(0).event().payload()).containsEntry("merged", true);
    }

    @Test
    void avulso_com_mesmo_telefone_mascarado_diferente_soma() {
        Long id = novo(avulso("85999991234", 1)).view().request().id();

        Detail d = service.addEntry(id, avulso("(85) 99999-1234", 2), "bia");

        assertThat(d.entries()).hasSize(1);
        assertThat(d.view().stats().totalQuantity()).isEqualTo(3);
    }

    @Test
    void item_fechado_nao_aceita_solicitacao() {
        Long id = novo(crm(7L, 1)).view().request().id();
        service.changeStatus(id, new StatusCommand(ProductRequestStatus.CANCELLED, null, null, "desistiu", false),
                "ana");

        assertThatThrownBy(() -> service.addEntry(id, crm(8L, 1), "bia"))
                .isInstanceOf(InvalidProductRequestTransitionException.class);
    }

    @Test
    void cancelar_exige_motivo_e_fecha_as_solicitacoes() {
        Long id = novo(crm(7L, 1)).view().request().id();
        assertThatThrownBy(() -> service.changeStatus(id,
                new StatusCommand(ProductRequestStatus.CANCELLED, null, null, " ", false), "ana"))
                .isInstanceOf(ProductRequestValidationException.class);

        Detail d = service.changeStatus(id,
                new StatusCommand(ProductRequestStatus.CANCELLED, null, null, "fornecedor parou", false), "ana");

        assertThat(d.view().request().closedAt()).isEqualTo(NOW);
        assertThat(d.entries()).allSatisfy(e ->
                assertThat(e.entry().status()).isEqualTo(ProductRequestEntryStatus.CANCELLED));
    }

    @Test
    void done_marca_as_ativas_como_atendidas() {
        Long id = novo(crm(7L, 1)).view().request().id();
        service.changeStatus(id, new StatusCommand(ProductRequestStatus.ARRIVED, null, null, null, true), "ana");

        Detail d = service.changeStatus(id, new StatusCommand(ProductRequestStatus.DONE, null, null, null, false),
                "ana");

        assertThat(d.entries()).allSatisfy(e ->
                assertThat(e.entry().status()).isEqualTo(ProductRequestEntryStatus.FULFILLED));
    }

    @Test
    void arrived_dispara_so_para_cliente_do_crm_ativo_e_nao_avisado() {
        comAutomacaoAtiva();
        Long id = novo(crm(7L, 1)).view().request().id();
        service.addEntry(id, avulso("85999991234", 1), "bia");
        service.addEntry(id, crm(8L, 1), "bia");
        Long canceladaId = repository.entriesOf(id).stream().filter(e -> Long.valueOf(8L).equals(e.customerId()))
                .findFirst().orElseThrow().id();
        service.cancelEntry(id, canceladaId, "ana");

        Detail d = service.changeStatus(id, new StatusCommand(ProductRequestStatus.ARRIVED, null, null, null, true),
                "ana");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AutomationOccurrence>> captor = ArgumentCaptor.forClass(List.class);
        verify(queue, atLeastOnce()).enqueue(captor.capture());
        List<AutomationOccurrence> sent = captor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(sent).singleElement().satisfies(o -> {
            assertThat(o.evento()).isEqualTo(AutomationEvent.PEDIDO_PRODUTO_CHEGOU);
            assertThat(o.customerId()).isEqualTo(7L);
            assertThat(o.variaveis()).containsKey("produto");
        });
        assertThat(d.view().stats().pendingNotifications()).isEqualTo(1); // o avulso fica para o aviso manual
    }

    @Test
    void sem_automacao_ativa_nao_marca_nada() {
        Long id = novo(crm(7L, 1)).view().request().id();
        service.changeStatus(id, new StatusCommand(ProductRequestStatus.ARRIVED, null, null, null, true), "ana");

        NotifyResult r = service.notify(id, null, ProductRequestNotifyChannel.AUTOMATION, "ana");

        assertThat(r.skippedNoAutomation()).isTrue();
        assertThat(r.notified()).isZero();
        assertThat(r.request().view().stats().pendingNotifications()).isEqualTo(1);
        verify(queue, never()).enqueue(any());
    }

    @Test
    void aviso_manual_so_registra() {
        Long id = novo(avulso("85999991234", 1)).view().request().id();

        NotifyResult r = service.notify(id, null, ProductRequestNotifyChannel.MANUAL, "ana");

        assertThat(r.notified()).isEqualTo(1);
        assertThat(r.request().entries().get(0).entry().notifiedChannel())
                .isEqualTo(ProductRequestNotifyChannel.MANUAL);
        verify(queue, never()).enqueue(any());
    }

    @Test
    void automacao_pula_cliente_avulso() {
        comAutomacaoAtiva();
        Long id = novo(avulso("85999991234", 1)).view().request().id();

        NotifyResult r = service.notify(id, null, ProductRequestNotifyChannel.AUTOMATION, "ana");

        assertThat(r.notified()).isZero();
        assertThat(r.skippedNoConsent()).isEqualTo(1);
        assertThat(r.skippedNoAutomation()).isFalse();
    }

    @Test
    void link_sku_valida_no_catalogo() {
        Long id = novo(crm(7L, 1)).view().request().id();
        assertThatThrownBy(() -> service.linkSku(id, "NAO-EXISTE", "ana"))
                .isInstanceOf(ProductRequestSkuNotFoundException.class);

        Product product = mock(Product.class);
        when(product.sku()).thenReturn("ZIG-66");
        when(product.name()).thenReturn("Essência Ziggy Love 66 50g");
        when(productRepository.findByAnySku("zig-66")).thenReturn(Optional.of(product));
        when(productRepository.findByAnySku("ZIG-66")).thenReturn(Optional.of(product));

        Detail d = service.linkSku(id, "zig-66", "ana");

        assertThat(d.view().request().linkedSku()).isEqualTo("ZIG-66");
        assertThat(d.view().linkedProductName()).isEqualTo("Essência Ziggy Love 66 50g");
        assertThat(d.events().get(0).event().type()).isEqualTo(ProductRequestEventType.LINKED_SKU);
    }

    @Test
    void similar_sugere_item_aberto_parecido() {
        Long id = novo(crm(7L, 1)).view().request().id();
        service.create(new CreateCommand("Rosh Kaloud Lotus", null, null, null, null, crm(8L, 1)), "ana");

        var similar = service.similar("essencia zigy love", null, null, null);

        assertThat(similar).singleElement().satisfies(s -> {
            assertThat(s.request().id()).isEqualTo(id);
            assertThat(s.entriesCount()).isEqualTo(1);
        });
    }

    @Test
    void entrada_de_estoque_move_ordered_vinculado_para_arrived() {
        Long id = novo(crm(7L, 1)).view().request().id();
        Product product = mock(Product.class);
        when(product.sku()).thenReturn("ZIG-66");
        when(productRepository.findByAnySku("ZIG-66")).thenReturn(Optional.of(product));
        service.linkSku(id, "ZIG-66", "ana");
        service.changeStatus(id, new StatusCommand(ProductRequestStatus.ORDERED, null, null, null, false), "ana");

        int moved = service.arriveByStockEntry(List.of("ZIG-66"));

        assertThat(moved).isEqualTo(1);
        Detail d = service.get(id);
        assertThat(d.view().request().status()).isEqualTo(ProductRequestStatus.ARRIVED);
        assertThat(d.events().get(0).event().actor()).isEqualTo(ProductRequestEvent.SYSTEM_ACTOR);
    }

    @Test
    void data_limite_no_passado_e_recusada() {
        assertThatThrownBy(() -> novo(new EntryCommand(7L, null, null, 1, null, null, LocalDate.of(2026, 11, 1),
                null))).isInstanceOf(ProductRequestValidationException.class);
    }

    // ── Repositório em memória ───────────────────────────────────────────────

    static final class InMemoryRepository implements ProductRequestRepository {
        private final Map<Long, ProductRequest> requests = new HashMap<>();
        private final Map<Long, ProductRequestEntry> entries = new HashMap<>();
        private final List<ProductRequestEvent> events = new ArrayList<>();
        private final AtomicLong seq = new AtomicLong();

        List<ProductRequestEntry> entriesOf(Long requestId) {
            return findEntries(requestId);
        }

        @Override
        public ProductRequest save(ProductRequest r) {
            long id = r.id() == null ? seq.incrementAndGet() : r.id();
            ProductRequest saved = new ProductRequest(id, r.description(), r.brand(), r.variation(), r.categoryId(),
                    r.categoryOther(), null, r.status(), r.linkedSku(), r.expectedAt(), r.supplierNote(),
                    r.createdBy(), r.createdAt(), r.updatedAt(), r.closedAt(), r.id() == null ? 0 : r.version() + 1);
            requests.put(id, saved);
            return saved;
        }

        @Override
        public Optional<ProductRequest> findById(Long id) {
            return Optional.ofNullable(requests.get(id));
        }

        @Override
        public List<ProductRequest> findByIds(Collection<Long> ids) {
            return ids.stream().map(requests::get).filter(java.util.Objects::nonNull).toList();
        }

        @Override
        public ProductRequestEntry saveEntry(ProductRequestEntry e) {
            long id = e.id() == null ? seq.incrementAndGet() : e.id();
            ProductRequestEntry saved = new ProductRequestEntry(id, e.requestId(), e.customerId(), e.customerName(),
                    e.customerPhone(), e.quantity(), e.maxPrice(), e.urgency(), e.neededBy(), e.note(), e.status(),
                    e.notifiedAt(), e.notifiedChannel(), e.createdBy(), e.createdAt());
            entries.put(id, saved);
            return saved;
        }

        @Override
        public List<ProductRequestEntry> findEntries(Long requestId) {
            return entries.values().stream().filter(e -> e.requestId().equals(requestId))
                    .sorted(java.util.Comparator.comparing(ProductRequestEntry::id)).toList();
        }

        @Override
        public ProductRequestEvent saveEvent(ProductRequestEvent e) {
            ProductRequestEvent saved = new ProductRequestEvent(seq.incrementAndGet(), e.requestId(), e.entryId(),
                    e.type(), e.fromStatus(), e.toStatus(), e.payload(), e.actor(), e.at());
            events.add(saved);
            return saved;
        }

        @Override
        public List<ProductRequestEvent> findEvents(Long requestId) {
            return events.stream().filter(e -> e.requestId().equals(requestId))
                    .sorted(java.util.Comparator.comparing(ProductRequestEvent::id).reversed()).toList();
        }

        @Override
        public PageResult<Row> search(Collection<ProductRequestStatus> statuses, String q, Long categoryId,
                ProductRequestSort sort, SortDirection direction, int page, int size) {
            List<Row> rows = requests.values().stream().filter(r -> statuses.contains(r.status()))
                    .map(r -> new Row(r, ProductRequestStats.of(findEntries(r.id())))).toList();
            return new PageResult<>(rows, page, size, rows.size(), 1);
        }

        @Override
        public Map<Long, ProductRequestStats> statsFor(Collection<Long> ids) {
            Map<Long, ProductRequestStats> out = new HashMap<>();
            ids.forEach(id -> out.put(id, ProductRequestStats.of(findEntries(id))));
            return out;
        }

        @Override
        public List<ProductRequest> findOpen() {
            return requests.values().stream().filter(ProductRequest::isOpen).toList();
        }

        @Override
        public Map<ProductRequestStatus, Long> countByStatus() {
            Map<ProductRequestStatus, Long> out = new EnumMap<>(ProductRequestStatus.class);
            requests.values().forEach(r -> out.merge(r.status(), 1L, Long::sum));
            return out;
        }

        @Override
        public long countArrivedAwaitingNotification() {
            return 0;
        }

        @Override
        public List<Row> topRequested(int limit) {
            return List.of();
        }

        @Override
        public List<ProductRequest> findByCustomer(Long customerId) {
            return List.of();
        }

        @Override
        public List<ProductRequest> findOrderedByLinkedSkus(Collection<String> skus) {
            return requests.values().stream()
                    .filter(r -> r.status() == ProductRequestStatus.ORDERED && skus.contains(r.linkedSku()))
                    .toList();
        }
    }
}
