package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitTemplateNameException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException.Reason;
import com.cernecommerce.core.domain.exception.estoque.KitTemplateNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.model.Money;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.pedido.DiscountProration;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.KitTemplateRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kit montável (EST-F031). Ver {@link KitTemplate} para por que isto não é o kit de EST-F015.
 *
 * <p><b>Toda regra de "o que pode entrar no kit" mora em {@link #quote}.</b> Carrinho, checkout e
 * comanda chamam o mesmo método, e o checkout chama de novo mesmo que o carrinho já tenha
 * validado — o preço nunca é guardado no carrinho (ECM-F003), e o produto pode ter sido
 * desativado ou mudado de categoria entre um e outro.</p>
 */
public class KitBuilderService implements KitBuilderUseCase {

    /** Teto de opções por passo. Passo de kit é uma categoria de acessório, não o catálogo inteiro. */
    static final int MAX_OPTIONS_PER_STEP = 100;

    private final KitTemplateRepository kitTemplateRepository;
    private final CategoryRepository categoryRepository;
    private final EstoqueUseCase estoqueUseCase;

    public KitBuilderService(KitTemplateRepository kitTemplateRepository, CategoryRepository categoryRepository,
            EstoqueUseCase estoqueUseCase) {
        this.kitTemplateRepository = kitTemplateRepository;
        this.categoryRepository = categoryRepository;
        this.estoqueUseCase = estoqueUseCase;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitTemplate> listTemplates() {
        return kitTemplateRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitTemplate> listSellableTemplates(KitChannel channel) {
        return kitTemplateRepository.findAll().stream().filter(t -> t.sellableIn(channel)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public KitTemplate getTemplate(Long id) {
        return kitTemplateRepository.findById(id).orElseThrow(() -> new KitTemplateNotFoundException(id));
    }

    @Override
    @Transactional(readOnly = true)
    public KitTemplate getSellableTemplate(Long id, KitChannel channel) {
        KitTemplate template = getTemplate(id);
        if (!template.sellableIn(channel)) {
            throw new KitTemplateNotFoundException(id);
        }
        return template;
    }

    @Override
    @Transactional
    public KitTemplate createTemplate(KitTemplate template) {
        validateForSave(null, template);
        return kitTemplateRepository.save(template.withId(null));
    }

    @Override
    @Transactional
    public KitTemplate updateTemplate(Long id, KitTemplate template) {
        getTemplate(id);
        validateForSave(id, template);
        return kitTemplateRepository.save(template.withId(id));
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        getTemplate(id);
        // Sem checagem de "em uso": carrinho, comanda e pedido guardam o id do modelo só como
        // rótulo do pacote, sem FK. Apagar o modelo não desfaz venda nenhuma — só impede novas.
        kitTemplateRepository.deleteById(id);
    }

    private void validateForSave(Long id, KitTemplate template) {
        kitTemplateRepository.findByNameIgnoreCase(template.name())
                .filter(existing -> !existing.id().equals(id))
                .ifPresent(existing -> {
                    throw new DuplicateKitTemplateNameException(template.name());
                });
        for (KitTemplateStep step : template.steps()) {
            if (categoryRepository.findById(step.categoryId()).isEmpty()) {
                throw new CategoryNotFoundException(step.categoryId());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<StepOption> listStepOptions(Long templateId, Long stepId, KitChannel channel,
            String warehouseCode) {
        KitTemplate template = getSellableTemplate(templateId, channel);
        KitTemplateStep step = template.step(stepId).orElseThrow(() -> new InvalidKitSelectionException(
                Reason.KIT_STEP_NOT_FOUND, "Passo " + stepId + " não pertence ao kit " + templateId));

        PageResult<Product> products = estoqueUseCase.listActivePricedProducts(0, MAX_OPTIONS_PER_STEP, null,
                step.categoryId(), null);
        List<StepOption> options = new ArrayList<>();
        for (Product product : products.content()) {
            if (!isSellableInKit(product, channel)) {
                continue;
            }
            List<ProductVariant> variants = product.variants().stream().filter(ProductVariant::active).toList();
            if (variants.isEmpty()) {
                options.add(toOption(product, product.sku(), List.of(), warehouseCode));
            } else {
                for (ProductVariant variant : variants) {
                    options.add(toOption(product, variant.sku(), variant.attributes(), warehouseCode));
                }
            }
        }
        return options;
    }

    private StepOption toOption(Product product, String sku,
            List<ProductAttribute> attributes, String warehouseCode) {
        Boolean available = warehouseCode == null ? null
                : estoqueUseCase.getStockBalance(sku, warehouseCode).availableQuantity().signum() > 0;
        return new StepOption(sku, product.sku(), product.name(), attributes,
                product.effectivePricingFor(sku).effectivePrice(), product.imageUrl(), available);
    }

    @Override
    @Transactional(readOnly = true)
    public KitQuote quote(KitSelection selection, KitChannel channel) {
        KitTemplate template = kitTemplateRepository.findById(selection.templateId())
                .filter(t -> t.sellableIn(channel))
                .orElseThrow(() -> new InvalidKitSelectionException(Reason.KIT_NOT_AVAILABLE,
                        "Kit " + selection.templateId() + " não está disponível"));
        if (selection.picks().isEmpty()) {
            throw new InvalidKitSelectionException(Reason.KIT_EMPTY, "Nenhum item escolhido para o kit");
        }

        Map<Long, Integer> picksPerStep = new HashMap<>();
        List<KitQuote.Line> undiscounted = new ArrayList<>(selection.picks().size());
        for (KitSelection.Pick pick : selection.picks()) {
            KitTemplateStep step = template.step(pick.stepId()).orElseThrow(() -> new InvalidKitSelectionException(
                    Reason.KIT_STEP_NOT_FOUND, "Passo " + pick.stepId() + " não pertence ao kit " + template.id()));
            int count = picksPerStep.merge(step.id(), 1, Integer::sum);
            if (count > step.maxItems()) {
                throw new InvalidKitSelectionException(Reason.KIT_STEP_MAX_ITEMS_EXCEEDED,
                        "Passo '" + step.name() + "' aceita no máximo " + step.maxItems() + " item(ns)");
            }
            undiscounted.add(resolveLine(step, pick.sku(), channel));
        }
        for (KitTemplateStep step : template.steps()) {
            if (step.required() && !picksPerStep.containsKey(step.id())) {
                throw new InvalidKitSelectionException(Reason.KIT_REQUIRED_STEP_MISSING,
                        "Passo obrigatório sem escolha: " + step.name());
            }
        }

        List<BigDecimal> amounts = undiscounted.stream().map(KitQuote.Line::unitPrice).toList();
        BigDecimal subtotal = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = subtotal.multiply(template.discountPercent())
                .divide(Money.HUNDRED, Money.MONEY_SCALE, Money.ROUNDING);
        // Mesmo rateio do desconto de conta do PDV (PDV-F014): fecha no centavo e nunca dá a uma
        // linha mais desconto que o valor dela.
        List<BigDecimal> lineDiscounts = DiscountProration.distribute(amounts, discount);
        List<KitQuote.Line> lines = new ArrayList<>(undiscounted.size());
        for (int i = 0; i < undiscounted.size(); i++) {
            KitQuote.Line l = undiscounted.get(i);
            lines.add(new KitQuote.Line(l.stepId(), l.stepName(), l.sku(), l.productName(), l.unitPrice(),
                    lineDiscounts.get(i)));
        }
        return new KitQuote(template, lines, subtotal, discount, subtotal.subtract(discount));
    }

    private KitQuote.Line resolveLine(KitTemplateStep step, String sku, KitChannel channel) {
        Product product;
        try {
            product = estoqueUseCase.findProductBySku(sku);
        } catch (ProductNotFoundException e) {
            throw notSellable(sku);
        }
        if (!isSellableInKit(product, channel)) {
            throw notSellable(sku);
        }
        // SKU de variação exige a variação ativa — o produto pai ativo não basta.
        Optional<ProductVariant> variant = product.variants().stream().filter(v -> v.sku().equals(sku)).findFirst();
        if (variant.isPresent() && !variant.get().active()) {
            throw notSellable(sku);
        }
        // Categoria é do pai: a variação herda (ver Product#categoryId).
        if (!step.categoryId().equals(product.categoryId())) {
            throw new InvalidKitSelectionException(Reason.KIT_ITEM_NOT_IN_STEP_CATEGORY,
                    "Produto " + sku + " não pertence ao passo '" + step.name() + "'");
        }
        Pricing pricing = product.effectivePricingFor(sku);
        if (!pricing.isPriced()) {
            throw notSellable(sku);
        }
        return new KitQuote.Line(step.id(), step.name(), sku, product.name(), pricing.effectivePrice(),
                BigDecimal.ZERO);
    }

    /**
     * Ativo, publicado, não-kit e visível no canal. Kit de EST-F015 fica de fora: dentro de um pacote
     * ele explodiria em componentes que o cliente não escolheu.
     */
    private static boolean isSellableInKit(Product product, KitChannel channel) {
        // EST-F054 — status != ATIVO cobre rascunho e arquivado.
        if (!product.active() || product.status() != ProductStatus.ATIVO || product.type() == ProductType.KIT) {
            return false;
        }
        return channel == KitChannel.PDV ? product.visibleInPos() : product.visibleInMarketplace();
    }

    private static InvalidKitSelectionException notSellable(String sku) {
        return new InvalidKitSelectionException(Reason.KIT_ITEM_NOT_SELLABLE,
                "Produto " + sku + " não está disponível para venda");
    }
}
