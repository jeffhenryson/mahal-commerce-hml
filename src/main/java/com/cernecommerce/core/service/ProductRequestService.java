package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestSkuNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestValidationException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductRequest;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntry;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntryStatus;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEvent;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEventType;
import com.cernecommerce.core.domain.model.estoque.ProductRequestKeys;
import com.cernecommerce.core.domain.model.estoque.ProductRequestNotifyChannel;
import com.cernecommerce.core.domain.model.estoque.ProductRequestSort;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStats;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.in.ProductRequestUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.crm.AutomationDispatchQueue;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRepository;
import com.cernecommerce.core.ports.out.estoque.ProductRequestRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pedidos de clientes. Ver {@link ProductRequestUseCase}.
 *
 * <p><b>Aviso por automação:</b> só marca {@code notified_at} quando existe automação ativa de
 * {@code PEDIDO_PRODUTO_CHEGOU} que aceita o estágio do cliente e o cliente tem contato. O disparo em si sai
 * depois do commit, fora da thread da requisição ({@link AutomationDispatchQueue}). Não há campo de
 * consentimento no CRM: "sem consentimento" é cliente avulso, anonimizado (V206), sem contato ou fora do
 * segmento das automações.</p>
 */
public class ProductRequestService implements ProductRequestUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProductRequestService.class);

    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final int SIMILAR_LIMIT = 5;
    private static final int TOP_LIMIT = 5;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String NOTIFY_BATCH_KEY = "product-request-notify";

    private final ProductRequestRepository repository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final CampaignAutomationRepository automationRepository;
    private final AutomationDispatchQueue dispatchQueue;
    private final AfterCommitExecutor afterCommit;
    private final Clock clock;

    public ProductRequestService(ProductRequestRepository repository, ProductRepository productRepository,
            CategoryRepository categoryRepository, CustomerRepository customerRepository, UserRepository userRepository,
            CampaignAutomationRepository automationRepository, AutomationDispatchQueue dispatchQueue,
            AfterCommitExecutor afterCommit, Clock clock) {
        this.repository = repository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.automationRepository = automationRepository;
        this.dispatchQueue = dispatchQueue;
        this.afterCommit = afterCommit;
        this.clock = clock;
    }

    // ── Leitura ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResult<View> list(Set<ProductRequestStatus> statuses, String q, Long categoryId, ProductRequestSort sort,
            SortDirection direction, int page, int size) {
        Set<ProductRequestStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(ProductRequestStatus.class) : statuses;
        String key = ProductRequestKeys.normalizeText(q);
        PageResult<ProductRequestRepository.Row> rows = repository.search(filter, key.isEmpty() ? null : key,
                categoryId, sort == null ? ProductRequestSort.CREATED_AT : sort,
                direction == null ? SortDirection.DESC : direction, Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        List<View> views = views(rows.content());
        return new PageResult<>(views, rows.page(), rows.size(), rows.totalElements(), rows.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public Dashboard summary() {
        Map<ProductRequestStatus, Long> counts = repository.countByStatus();
        List<Top> top = repository.topRequested(TOP_LIMIT).stream()
                .map(r -> new Top(r.request().id(), r.request().description(), r.stats().entriesCount()))
                .toList();
        return new Dashboard(counts.getOrDefault(ProductRequestStatus.PENDING, 0L),
                counts.getOrDefault(ProductRequestStatus.ORDERED, 0L),
                counts.getOrDefault(ProductRequestStatus.ARRIVED, 0L),
                repository.countArrivedAwaitingNotification(), top);
    }

    @Override
    @Transactional(readOnly = true)
    public Detail get(Long id) {
        return detail(require(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Similar> similar(String description, String brand, String variation, String sku) {
        String key = ProductRequestKeys.normalize(description, brand, variation);
        String wantedSku = sku == null || sku.isBlank() ? null : sku.trim();
        if (key.isEmpty() && wantedSku == null) {
            return List.of();
        }
        // Item já vinculado ao SKU informado conta como similaridade 1 e vem primeiro.
        List<Similar> candidates = new ArrayList<>();
        for (ProductRequest open : repository.findOpen()) {
            boolean sameSku = wantedSku != null && wantedSku.equalsIgnoreCase(open.linkedSku());
            double similarity = sameSku ? 1.0 : ProductRequestKeys.similarity(key, open.normalizedKey());
            if (sameSku || similarity >= ProductRequestKeys.SIMILARITY_THRESHOLD) {
                candidates.add(new Similar(open, 0, Math.round(similarity * 1000) / 1000.0));
            }
        }
        List<Similar> best = candidates.stream()
                .sorted(Comparator.comparing((Similar c) -> wantedSku != null
                                && wantedSku.equalsIgnoreCase(c.request().linkedSku())).reversed()
                        .thenComparing(Comparator.comparingDouble(Similar::similarity).reversed())
                        .thenComparing(c -> c.request().id()))
                .limit(SIMILAR_LIMIT)
                .toList();
        Map<Long, ProductRequestStats> stats = repository.statsFor(best.stream().map(c -> c.request().id()).toList());
        return best.stream()
                .map(c -> new Similar(c.request(), stats.getOrDefault(c.request().id(), ProductRequestStats.EMPTY)
                        .entriesCount(), c.similarity()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<View> byCustomer(Long customerId) {
        customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        List<ProductRequest> requests = repository.findByCustomer(customerId);
        Map<Long, ProductRequestStats> stats = repository.statsFor(requests.stream().map(ProductRequest::id).toList());
        return views(requests.stream()
                .map(r -> new ProductRequestRepository.Row(r, stats.getOrDefault(r.id(), ProductRequestStats.EMPTY)))
                .toList());
    }

    // ── Escrita ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Detail create(CreateCommand command, String actor) {
        Instant now = clock.instant();
        requireCategory(command.categoryId(), command.categoryOther());
        ProductRequestEntry draft = newEntry(null, command.entry(), actor, now);
        ProductRequest saved = repository.save(ProductRequest.create(command.description(), command.brand(),
                command.variation(), command.categoryId(), command.categoryOther(), actor, now));
        ProductRequestEntry entry = repository.saveEntry(withRequestId(draft, saved.id()));
        repository.saveEvent(ProductRequestEvent.of(saved.id(), entry.id(), ProductRequestEventType.CREATED,
                payload("quantity", entry.quantity()), actor, now));
        return detail(saved);
    }

    @Override
    @Transactional
    public Detail addEntry(Long id, EntryCommand command, String actor) {
        Instant now = clock.instant();
        ProductRequest request = require(id);
        request.requireOpen();
        ProductRequestEntry incoming = newEntry(id, command, actor, now);
        ProductRequestEntry existing = repository.findEntries(id).stream()
                .filter(ProductRequestEntry::isActive)
                .filter(e -> e.sameCustomerAs(incoming))
                .findFirst().orElse(null);
        ProductRequestEntry saved;
        boolean merged = existing != null;
        if (merged) {
            saved = repository.saveEntry(existing.mergeWith(incoming));
        } else {
            saved = repository.saveEntry(incoming);
        }
        ProductRequest touched = repository.save(request.touch(now));
        repository.saveEvent(ProductRequestEvent.of(id, saved.id(), ProductRequestEventType.ENTRY_ADDED,
                payload("quantity", incoming.quantity(), "merged", merged), actor, now));
        return detail(touched);
    }

    @Override
    @Transactional
    public Detail update(Long id, PatchCommand c, String actor) {
        Instant now = clock.instant();
        ProductRequest current = require(id);
        current.requireOpen();
        String description = c.description() == null ? current.description() : c.description().trim();
        if (description.length() < 2 || description.length() > 160) {
            throw new ProductRequestValidationException("A descrição deve ter de 2 a 160 caracteres");
        }
        Long categoryId = current.categoryId();
        String categoryOther = current.categoryOther();
        if (c.categoryId() != null && c.categoryOther() != null && !c.categoryOther().isBlank()) {
            throw new ProductRequestValidationException("Informe a categoria do catálogo ou a outra, não as duas");
        }
        if (c.categoryId() != null) {
            requireCategory(c.categoryId(), null);
            categoryId = c.categoryId();
            categoryOther = null;
        } else if (c.categoryOther() != null) {
            categoryOther = c.categoryOther();
            if (!c.categoryOther().isBlank()) {
                categoryId = null;
            }
        }
        ProductRequest updated = current.withDetails(description, keepOrClear(c.brand(), current.brand()),
                keepOrClear(c.variation(), current.variation()), categoryId, categoryOther,
                c.clearExpectedAt() ? null : c.expectedAt() != null ? c.expectedAt() : current.expectedAt(),
                keepOrClear(c.supplierNote(), current.supplierNote()), now);
        ProductRequest saved = repository.save(updated);
        repository.saveEvent(ProductRequestEvent.of(id, null, ProductRequestEventType.UPDATED,
                changes(current, saved), actor, now));
        return detail(saved);
    }

    @Override
    @Transactional
    public Detail changeStatus(Long id, StatusCommand c, String actor) {
        return detail(transition(require(id), c, actor, clock.instant()));
    }

    @Override
    @Transactional
    public Detail updateEntry(Long id, Long entryId, EntryPatchCommand c, String actor) {
        Instant now = clock.instant();
        ProductRequest request = require(id);
        request.requireOpen();
        ProductRequestEntry entry = requireEntry(id, entryId);
        requireActiveEntry(request, entry);
        if (c.neededBy() != null) {
            requireNotPast(c.neededBy());
        }
        ProductRequestEntry saved = repository.saveEntry(entry.patch(c.quantity(), c.maxPrice(), c.urgency(),
                c.neededBy(), c.note()));
        ProductRequest touched = repository.save(request.touch(now));
        repository.saveEvent(ProductRequestEvent.of(id, entryId, ProductRequestEventType.ENTRY_UPDATED,
                entryChanges(entry, saved), actor, now));
        return detail(touched);
    }

    @Override
    @Transactional
    public Detail cancelEntry(Long id, Long entryId, String actor) {
        Instant now = clock.instant();
        ProductRequest request = require(id);
        request.requireOpen();
        ProductRequestEntry entry = requireEntry(id, entryId);
        requireActiveEntry(request, entry);
        repository.saveEntry(entry.withStatus(ProductRequestEntryStatus.CANCELLED));
        ProductRequest touched = repository.save(request.touch(now));
        repository.saveEvent(ProductRequestEvent.of(id, entryId, ProductRequestEventType.ENTRY_CANCELLED, null,
                actor, now));
        return detail(touched);
    }

    @Override
    @Transactional
    public Detail linkSku(Long id, String sku, String actor) {
        Instant now = clock.instant();
        ProductRequest request = require(id);
        request.requireOpen();
        String typed = sku == null || sku.isBlank() ? null : sku.trim();
        String normalized = null;
        if (typed != null) {
            Product product = productRepository.findByAnySku(typed)
                    .orElseThrow(() -> new ProductRequestSkuNotFoundException(typed));
            normalized = canonicalSku(product, typed);
        }
        if (Objects.equals(normalized, request.linkedSku())) {
            return detail(request);
        }
        ProductRequest saved = repository.save(request.withLinkedSku(normalized, now));
        repository.saveEvent(ProductRequestEvent.of(id, null, ProductRequestEventType.LINKED_SKU,
                payload("sku", normalized, "previousSku", request.linkedSku()), actor, now));
        return detail(saved);
    }

    @Override
    @Transactional
    public NotifyResult notify(Long id, Collection<Long> entryIds, ProductRequestNotifyChannel channel, String actor) {
        Instant now = clock.instant();
        ProductRequest request = require(id);
        request.requireOpen();
        List<ProductRequestEntry> entries = repository.findEntries(id);
        List<ProductRequestEntry> targets;
        if (entryIds == null || entryIds.isEmpty()) {
            targets = entries.stream().filter(ProductRequestEntry::awaitsNotification).toList();
        } else {
            Map<Long, ProductRequestEntry> byId = entries.stream()
                    .collect(Collectors.toMap(ProductRequestEntry::id, Function.identity()));
            targets = new ArrayList<>();
            for (Long entryId : new java.util.LinkedHashSet<>(entryIds)) {
                ProductRequestEntry e = byId.get(entryId);
                if (e == null) {
                    throw new ProductRequestNotFoundException(id, entryId);
                }
                if (e.isActive()) {
                    targets.add(e);
                }
            }
        }
        NotifyResult outcome = notifyEntries(request, targets,
                channel == null ? ProductRequestNotifyChannel.AUTOMATION : channel, actor, now);
        ProductRequest current = outcome.notified() > 0 ? repository.save(request.touch(now)) : request;
        return new NotifyResult(outcome.notified(), outcome.skippedNoConsent(), outcome.skippedNoAutomation(),
                detail(current));
    }

    @Override
    @Transactional
    public int arriveByStockEntry(Collection<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return 0;
        }
        int moved = 0;
        for (ProductRequest request : repository.findOrderedByLinkedSkus(new HashSet<>(skus))) {
            transition(request, new StatusCommand(ProductRequestStatus.ARRIVED, null, null, null, false),
                    ProductRequestEvent.SYSTEM_ACTOR, clock.instant());
            moved++;
        }
        if (moved > 0) {
            log.info("product-request.auto-arrive skus={} moved={}", skus, moved);
        }
        return moved;
    }

    // ── Transição e aviso ────────────────────────────────────────────────────

    private ProductRequest transition(ProductRequest current, StatusCommand c, String actor, Instant now) {
        ProductRequestStatus target = c.status();
        if (target == ProductRequestStatus.CANCELLED && (c.reason() == null || c.reason().isBlank())) {
            throw new ProductRequestValidationException("Informe o motivo do cancelamento");
        }
        ProductRequest moved = current.transitionTo(target, c.expectedAt(),
                c.supplierNote() == null || c.supplierNote().isBlank() ? null : c.supplierNote(), c.withoutSku(), now);
        ProductRequest saved = repository.save(moved);

        Map<String, Object> eventPayload = new LinkedHashMap<>();
        if (c.reason() != null && !c.reason().isBlank()) {
            eventPayload.put("reason", c.reason().trim());
        }
        if (c.expectedAt() != null) {
            eventPayload.put("expectedAt", c.expectedAt().toString());
        }
        if (c.withoutSku()) {
            eventPayload.put("withoutSku", true);
        }
        repository.saveEvent(ProductRequestEvent.statusChanged(saved.id(), current.status(), target, eventPayload,
                actor, now));

        List<ProductRequestEntry> entries = repository.findEntries(saved.id());
        if (target == ProductRequestStatus.DONE || target == ProductRequestStatus.CANCELLED) {
            ProductRequestEntryStatus closeAs = target == ProductRequestStatus.DONE
                    ? ProductRequestEntryStatus.FULFILLED : ProductRequestEntryStatus.CANCELLED;
            entries.stream().filter(ProductRequestEntry::isActive)
                    .forEach(e -> repository.saveEntry(e.withStatus(closeAs)));
        } else if (target == ProductRequestStatus.ARRIVED) {
            // Disparo automático só para cliente do CRM; o avulso fica para o aviso manual.
            List<ProductRequestEntry> pending = entries.stream()
                    .filter(ProductRequestEntry::awaitsNotification)
                    .filter(ProductRequestEntry::isRegistered)
                    .toList();
            notifyEntries(saved, pending, ProductRequestNotifyChannel.AUTOMATION, actor, now);
        }
        return saved;
    }

    /** Resultado sem o detalhe ({@code request} nulo): quem chama monta o detalhe no fim. */
    private NotifyResult notifyEntries(ProductRequest request, List<ProductRequestEntry> targets,
            ProductRequestNotifyChannel channel, String actor, Instant now) {
        if (targets.isEmpty()) {
            return new NotifyResult(0, 0, false, null);
        }
        if (channel == ProductRequestNotifyChannel.MANUAL) {
            targets.forEach(e -> repository.saveEntry(e.notified(ProductRequestNotifyChannel.MANUAL, now)));
            repository.saveEvent(ProductRequestEvent.of(request.id(), targets.size() == 1 ? targets.get(0).id() : null,
                    ProductRequestEventType.NOTIFIED, payload("channel", channel.name(), "count", targets.size()),
                    actor, now));
            return new NotifyResult(targets.size(), 0, false, null);
        }

        List<CampaignAutomation> automations = automationRepository.findActiveByEvent(
                AutomationEvent.PEDIDO_PRODUTO_CHEGOU);
        if (automations.isEmpty()) {
            return new NotifyResult(0, 0, true, null);
        }
        Map<Long, Customer> customers = customersOf(targets);
        String productName = linkedProductName(request);
        int skipped = 0;
        List<ProductRequestEntry> notified = new ArrayList<>();
        for (ProductRequestEntry entry : targets) {
            Customer customer = entry.customerId() == null ? null : customers.get(entry.customerId());
            boolean reachable = customer != null && !customer.isAnonymized()
                    && (notBlank(customer.contato()) || notBlank(customer.email()))
                    && automations.stream().anyMatch(a -> a.acceptsStage(customer.estagio()));
            if (!reachable) {
                skipped++;
                continue;
            }
            notified.add(repository.saveEntry(entry.notified(ProductRequestNotifyChannel.AUTOMATION, now)));
            AutomationOccurrence occurrence = AutomationOccurrence.event(AutomationEvent.PEDIDO_PRODUTO_CHEGOU,
                    customer.id(), "PEDIDO_PRODUTO:" + entry.id() + ":" + now.toEpochMilli(),
                    Map.of("pedidoProdutoId", request.id(), "solicitacaoId", entry.id()),
                    templateVariables(request, productName, entry));
            afterCommit.accumulate(NOTIFY_BATCH_KEY, occurrence, dispatchQueue::enqueue);
        }
        if (!notified.isEmpty()) {
            repository.saveEvent(ProductRequestEvent.of(request.id(),
                    notified.size() == 1 ? notified.get(0).id() : null, ProductRequestEventType.NOTIFIED,
                    payload("channel", channel.name(), "count", notified.size(), "skippedNoConsent", skipped),
                    actor, now));
        }
        return new NotifyResult(notified.size(), skipped, false, null);
    }

    static Map<String, Object> templateVariables(ProductRequest request, String linkedProductName,
            ProductRequestEntry entry) {
        Map<String, Object> produto = new LinkedHashMap<>();
        produto.put("nome", linkedProductName != null ? linkedProductName : request.description());
        produto.put("marca", request.brand());
        produto.put("variacao", request.variation());
        Map<String, Object> pedidoProduto = new LinkedHashMap<>();
        pedidoProduto.put("id", request.id());
        pedidoProduto.put("quantidade", entry.quantity());
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("produto", produto);
        vars.put("pedidoProduto", pedidoProduto);
        return vars;
    }

    // ── Montagem das visões ──────────────────────────────────────────────────

    private Detail detail(ProductRequest request) {
        List<ProductRequestEntry> entries = new ArrayList<>(repository.findEntries(request.id()));
        entries.sort(Comparator.comparing((ProductRequestEntry e) -> !e.isActive())
                .thenComparing(ProductRequestEntry::createdAt));
        List<ProductRequestEvent> events = new ArrayList<>(repository.findEvents(request.id()));
        events.sort(Comparator.comparing(ProductRequestEvent::at).thenComparing(ProductRequestEvent::id,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        Set<String> usernames = new HashSet<>();
        usernames.add(request.createdBy());
        entries.forEach(e -> usernames.add(e.createdBy()));
        events.forEach(e -> usernames.add(e.actor()));
        Map<String, String> names = displayNames(usernames);
        Map<Long, Customer> customers = customersOf(entries);

        List<EntryView> entryViews = entries.stream().map(e -> {
            Customer customer = e.customerId() == null ? null : customers.get(e.customerId());
            String name = customer != null ? customer.nome() : e.customerName();
            String phone = customer != null ? phoneOf(customer) : e.customerPhone();
            return new EntryView(e, name == null ? "" : name, phone, names.getOrDefault(e.createdBy(), e.createdBy()));
        }).toList();
        List<EventView> eventViews = events.stream()
                .map(e -> new EventView(e, names.getOrDefault(e.actor(), e.actor())))
                .toList();
        View view = new View(request, ProductRequestStats.of(entries), categoryName(request, new HashMap<>()),
                linkedProductName(request));
        return new Detail(view, names.getOrDefault(request.createdBy(), request.createdBy()), entryViews, eventViews);
    }

    private List<View> views(List<ProductRequestRepository.Row> rows) {
        Set<String> skus = rows.stream().map(r -> r.request().linkedSku()).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, String> productNames = new HashMap<>();
        if (!skus.isEmpty()) {
            for (Product p : productRepository.findByAnySkuIn(skus)) {
                for (String sku : skus) {
                    if (matches(p, sku)) {
                        productNames.put(sku, p.name());
                    }
                }
            }
        }
        Map<Long, String> categoryCache = new HashMap<>();
        return rows.stream()
                .map(r -> new View(r.request(), r.stats(), categoryName(r.request(), categoryCache),
                        r.request().linkedSku() == null ? null : productNames.get(r.request().linkedSku())))
                .toList();
    }

    private String categoryName(ProductRequest request, Map<Long, String> cache) {
        if (request.categoryId() == null) {
            return request.categoryOther();
        }
        return cache.computeIfAbsent(request.categoryId(),
                id -> categoryRepository.findById(id).map(Category::name).orElse(null));
    }

    private String linkedProductName(ProductRequest request) {
        return request.linkedSku() == null ? null
                : productRepository.findByAnySku(request.linkedSku()).map(Product::name).orElse(null);
    }

    private Map<String, String> displayNames(Set<String> usernames) {
        Map<String, String> out = new HashMap<>();
        for (String username : usernames) {
            if (username == null || ProductRequestEvent.SYSTEM_ACTOR.equals(username)) {
                continue;
            }
            userRepository.findByUsername(username).map(User::getFullName).filter(ProductRequestService::notBlank)
                    .ifPresent(name -> out.put(username, name));
        }
        return out;
    }

    private Map<Long, Customer> customersOf(Collection<ProductRequestEntry> entries) {
        Set<Long> ids = entries.stream().map(ProductRequestEntry::customerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customerRepository.findByIds(ids).stream()
                .collect(Collectors.toMap(Customer::id, Function.identity(), (a, b) -> a));
    }

    /** O contato do CRM só serve de telefone se tiver cara de telefone (pode ser um e-mail). */
    private static String phoneOf(Customer customer) {
        if (customer.isAnonymized() || customer.contato() == null) {
            return null;
        }
        String digits = customer.contato().replaceAll("\\D", "");
        return digits.length() >= 10 && digits.length() <= 13 ? digits : null;
    }

    // ── Validações e auxiliares ──────────────────────────────────────────────

    private ProductRequest require(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductRequestNotFoundException(id));
    }

    private ProductRequestEntry requireEntry(Long id, Long entryId) {
        return repository.findEntries(id).stream().filter(e -> e.id().equals(entryId)).findFirst()
                .orElseThrow(() -> new ProductRequestNotFoundException(id, entryId));
    }

    private static void requireActiveEntry(ProductRequest request, ProductRequestEntry entry) {
        if (!entry.isActive()) {
            throw new com.cernecommerce.core.domain.exception.estoque.InvalidProductRequestTransitionException(
                    request.status());
        }
    }

    private void requireCategory(Long categoryId, String categoryOther) {
        if (categoryId != null && categoryOther != null && !categoryOther.isBlank()) {
            throw new ProductRequestValidationException("Informe a categoria do catálogo ou a outra, não as duas");
        }
        if (categoryId != null) {
            categoryRepository.findById(categoryId).orElseThrow(() -> new CategoryNotFoundException(categoryId));
        }
    }

    private ProductRequestEntry newEntry(Long requestId, EntryCommand c, String actor, Instant now) {
        if (c == null) {
            throw new ProductRequestValidationException("Informe a solicitação do cliente");
        }
        if (c.customerId() != null) {
            customerRepository.findById(c.customerId()).filter(cu -> !cu.isAnonymized())
                    .orElseThrow(() -> new CustomerNotFoundException(c.customerId()));
        }
        if (c.neededBy() != null) {
            requireNotPast(c.neededBy());
        }
        return ProductRequestEntry.create(requestId, c.customerId(), c.customerName(), c.customerPhone(),
                c.quantity(), c.maxPrice(), c.urgency(), c.neededBy(), c.note(), actor, now);
    }

    private void requireNotPast(LocalDate date) {
        if (date.isBefore(LocalDate.ofInstant(clock.instant(), ZONA_BRASIL))) {
            throw new ProductRequestValidationException("A data limite não pode estar no passado");
        }
    }

    private static ProductRequestEntry withRequestId(ProductRequestEntry e, Long requestId) {
        return new ProductRequestEntry(e.id(), requestId, e.customerId(), e.customerName(), e.customerPhone(),
                e.quantity(), e.maxPrice(), e.urgency(), e.neededBy(), e.note(), e.status(), e.notifiedAt(),
                e.notifiedChannel(), e.createdBy(), e.createdAt());
    }

    /** SKU como está no catálogo (a caixa digitada pode diferir). */
    private static String canonicalSku(Product product, String sku) {
        if (product.sku() != null && product.sku().equalsIgnoreCase(sku)) {
            return product.sku();
        }
        for (ProductVariant v : product.variants()) {
            if (v.sku() != null && v.sku().equalsIgnoreCase(sku)) {
                return v.sku();
            }
        }
        return sku;
    }

    private static boolean matches(Product product, String sku) {
        if (sku.equalsIgnoreCase(product.sku())) {
            return true;
        }
        return product.variants().stream().anyMatch(v -> sku.equalsIgnoreCase(v.sku()));
    }

    /** {@code null} mantém, {@code ""} limpa, o resto substitui. */
    private static String keepOrClear(String incoming, String current) {
        if (incoming == null) {
            return current;
        }
        return incoming.isBlank() ? null : incoming.trim();
    }

    private static Map<String, Object> changes(ProductRequest before, ProductRequest after) {
        Map<String, Object> out = new LinkedHashMap<>();
        diff(out, "description", before.description(), after.description());
        diff(out, "brand", before.brand(), after.brand());
        diff(out, "variation", before.variation(), after.variation());
        diff(out, "categoryId", before.categoryId(), after.categoryId());
        diff(out, "categoryOther", before.categoryOther(), after.categoryOther());
        diff(out, "expectedAt", before.expectedAt(), after.expectedAt());
        diff(out, "supplierNote", before.supplierNote(), after.supplierNote());
        return out;
    }

    private static Map<String, Object> entryChanges(ProductRequestEntry before, ProductRequestEntry after) {
        Map<String, Object> out = new LinkedHashMap<>();
        diff(out, "quantity", before.quantity(), after.quantity());
        diff(out, "maxPrice", before.maxPrice(), after.maxPrice());
        diff(out, "urgency", before.urgency(), after.urgency());
        diff(out, "neededBy", before.neededBy(), after.neededBy());
        diff(out, "note", before.note(), after.note());
        return out;
    }

    private static void diff(Map<String, Object> out, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            out.put(field, after == null ? null : after.toString());
        }
    }

    private static Map<String, Object> payload(Object... kv) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out.put((String) kv[i], kv[i + 1]);
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
