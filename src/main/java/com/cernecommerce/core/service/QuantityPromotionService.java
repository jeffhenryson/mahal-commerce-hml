package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.PromotionChangedException;
import com.cernecommerce.core.domain.model.pedido.DiscountProration;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.PdvUseCase.PromotionCheck;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.domain.exception.estoque.DuplicatePromotionNameException;
import com.cernecommerce.core.domain.exception.estoque.PromotionSkuInactiveException;
import com.cernecommerce.core.domain.exception.estoque.PromotionSkuNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.QuantityPromotionNotFoundException;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotionEngine;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.QuantityPromotionUseCase;
import com.cernecommerce.core.ports.out.estoque.QuantityPromotionRepository;
import com.cernecommerce.core.ports.out.pedido.PromotionStatsQuery;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuantityPromotionService implements QuantityPromotionUseCase {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(QuantityPromotionService.class);

    private final QuantityPromotionRepository repository;
    private final EstoqueUseCase estoqueUseCase;
    private final PromotionStatsQuery statsQuery;
    private final Clock clock;

    public QuantityPromotionService(QuantityPromotionRepository repository, EstoqueUseCase estoqueUseCase,
            PromotionStatsQuery statsQuery, Clock clock) {
        this.repository = repository;
        this.estoqueUseCase = estoqueUseCase;
        this.statsQuery = statsQuery;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuantityPromotion> list() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public QuantityPromotion get(Long id) {
        return repository.findById(id).orElseThrow(() -> new QuantityPromotionNotFoundException(id));
    }

    @Override
    @Transactional
    public QuantityPromotion create(QuantityPromotion promotion, String username) {
        validateForSave(null, promotion);
        Instant now = clock.instant();
        return repository.save(promotion.withId(null).withAudit(now, username, now, username));
    }

    @Override
    @Transactional
    public QuantityPromotion update(Long id, QuantityPromotion promotion, String username) {
        QuantityPromotion current = get(id);
        validateForSave(id, promotion);
        return repository.save(promotion.withId(id)
                .withAudit(current.createdAt(), current.createdBy(), clock.instant(), username));
    }

    @Override
    @Transactional
    public QuantityPromotion setActive(Long id, boolean active, String username) {
        QuantityPromotion current = get(id);
        if (current.active() == active) {
            return current;
        }
        // Reativar não revalida os SKUs: um que foi inativado continua na lista e só não conta.
        return repository.save(current.withActive(active)
                .withAudit(current.createdAt(), current.createdBy(), clock.instant(), username));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        get(id);
        // Sem checagem de "em uso": o pedido guarda promotion_id sem FK e o nome congelado.
        repository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuantityPromotion> listVigentes(PromotionChannel channel, Instant at) {
        Instant instant = at == null ? clock.instant() : at;
        return repository.findAllActive().stream().filter(p -> p.isVigente(instant, channel)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public QuantityPromotionEngine.Result calculate(PromotionChannel channel, Instant at,
            List<QuantityPromotionEngine.CartLine> lines, Map<String, Integer> unitsInKit) {
        if (lines == null || lines.isEmpty()) {
            return QuantityPromotionEngine.Result.EMPTY;
        }
        // Não filtra SKU inativo aqui: a linha só chegou ao carrinho se o SKU é vendável
        // (resolveSaleInfo), então um SKU inativo da regra nunca casa com linha nenhuma.
        List<QuantityPromotion> vigentes = listVigentes(channel, at);
        if (vigentes.isEmpty()) {
            return QuantityPromotionEngine.Result.EMPTY;
        }
        return QuantityPromotionEngine.apply(lines, vigentes, unitsInKit);
    }

    @Override
    @Transactional(readOnly = true)
    public PromotionStats stats(Long id, Instant from, Instant to) {
        QuantityPromotion promotion = get(id);
        PromotionStatsQuery.Totals t = statsQuery.totals(id, from, to);
        long grupos = promotion.quantity() == 0 ? 0 : t.unidades() / promotion.quantity();
        return new PromotionStats(t.vendas(), grupos, t.unidades(),
                t.descontoTotal() == null ? BigDecimal.ZERO : t.descontoTotal());
    }

    @Override
    @Transactional(readOnly = true)
    public PromotedSale applyToSale(Instant at, List<OrderItem> items, List<SaleItemCommand> commands,
            PromotionCheck check, String clientSaleId) {
        Map<String, Integer> unitsInKit = new HashMap<>();
        Map<String, BigDecimal> clientBySku = new HashMap<>();
        for (SaleItemCommand c : commands) {
            if (c.kitQuantity() != null && c.kitQuantity() > 0) {
                unitsInKit.merge(c.sku(), c.kitQuantity(), Integer::sum);
            }
            if (c.promotionDiscountAmount() != null && c.promotionDiscountAmount().signum() > 0) {
                clientBySku.merge(c.sku(), c.promotionDiscountAmount(), BigDecimal::add);
            }
        }
        QuantityPromotionEngine.Result result = calculate(PromotionChannel.PDV, at, items.stream()
                        .map(i -> new QuantityPromotionEngine.CartLine(i.sku(), i.quantity(), i.unitPrice()))
                        .toList(), unitsInKit);

        BigDecimal calculated = result.total();
        Map<String, BigDecimal> discountBySku = new HashMap<>();
        result.bySku().forEach((sku, p) -> discountBySku.put(sku, p.discount()));
        if (calculated.compareTo(check.expectedDiscount()) != 0) {
            if (!check.lenient()) {
                throw new PromotionChangedException(check.expectedDiscount(), calculated);
            }
            // Venda offline já cobrada: recusar não devolve o dinheiro. Vale o que o cliente aplicou.
            LOG.warn("Venda offline {} com promoção divergente: cliente {} x servidor {}. Aceito o valor do cliente.",
                    clientSaleId, check.expectedDiscount(), calculated);
            discountBySku.clear();
            discountBySku.putAll(clientBySku);
        }
        if (discountBySku.isEmpty()) {
            return new PromotedSale(items, BigDecimal.ZERO);
        }

        List<OrderItem> out = new ArrayList<>(items);
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : discountBySku.entrySet()) {
            List<Integer> idx = new ArrayList<>();
            List<BigDecimal> room = new ArrayList<>();
            for (int i = 0; i < out.size(); i++) {
                OrderItem item = out.get(i);
                if (item.sku().equals(e.getKey()) && item.grossAmount() != null) {
                    idx.add(i);
                    room.add(item.grossAmount().subtract(item.discountAmount()).max(BigDecimal.ZERO));
                }
            }
            BigDecimal capacity = room.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = e.getValue().min(capacity);
            if (idx.isEmpty() || amount.signum() <= 0) {
                continue;
            }
            List<BigDecimal> parts = DiscountProration.distribute(room, amount);
            QuantityPromotionEngine.SkuPromotion rule = result.bySku().get(e.getKey());
            for (int k = 0; k < idx.size(); k++) {
                int i = idx.get(k);
                out.set(i, out.get(i).withPromotion(rule == null ? null : rule.promotionId(),
                        rule == null ? null : rule.promotionName(), parts.get(k)));
            }
            total = total.add(amount);
        }
        return new PromotedSale(out, total);
    }

    private void validateForSave(Long id, QuantityPromotion promotion) {
        repository.findByNameIgnoreCase(promotion.name())
                .filter(existing -> !existing.id().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicatePromotionNameException(promotion.name());
                });
        Map<String, Product> owners = new HashMap<>();
        for (Product p : estoqueUseCase.findProductsByAnySku(promotion.skus())) {
            owners.put(p.sku(), p);
            p.variants().forEach(v -> owners.put(v.sku(), p));
        }
        for (String sku : promotion.skus()) {
            Product product = owners.get(sku);
            if (product == null) {
                throw new PromotionSkuNotFoundException(sku);
            }
            if (!isSellable(product, sku)) {
                throw new PromotionSkuInactiveException(sku);
            }
        }
    }

    /** Ativo, publicado, não-kit, variação ativa e, se for o SKU pai, vendável por si. */
    private static boolean isSellable(Product product, String sku) {
        if (!product.active() || product.status() != ProductStatus.ATIVO || product.type() == ProductType.KIT) {
            return false;
        }
        if (!product.isSellable(sku)) {
            return false;
        }
        Optional<ProductVariant> variant = product.variants().stream().filter(v -> v.sku().equals(sku)).findFirst();
        return variant.map(ProductVariant::active).orElse(true);
    }
}
