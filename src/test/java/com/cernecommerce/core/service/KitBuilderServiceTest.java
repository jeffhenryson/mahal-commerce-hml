package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.CategoryNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DuplicateKitTemplateNameException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException.Reason;
import com.cernecommerce.core.domain.exception.estoque.KitTemplateNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.KitSelection.Pick;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase.StepOption;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import com.cernecommerce.core.ports.out.estoque.KitTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KitBuilderServiceTest {

    private static final long BAG = 10L;
    private static final long SEDA = 20L;
    private static final long ISQUEIRO = 30L;
    private static final long CAT_BAG = 1L;
    private static final long CAT_SEDA = 2L;
    private static final long CAT_ISQUEIRO = 3L;

    @Mock KitTemplateRepository kitTemplateRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock EstoqueUseCase estoqueUseCase;

    private KitBuilderService service;

    /** Bag e seda obrigatórios, isqueiro opcional; 10% de desconto. */
    private final KitTemplate kitMahal = new KitTemplate(7L, "Kit Mahal", null, null, new BigDecimal("10"),
            true, true, true, List.of(
                    new KitTemplateStep(BAG, "Bag", 0, CAT_BAG, true, 1),
                    new KitTemplateStep(SEDA, "Seda", 1, CAT_SEDA, true, 2),
                    new KitTemplateStep(ISQUEIRO, "Isqueiro", 2, CAT_ISQUEIRO, false, 1)));

    @BeforeEach
    void setUp() {
        service = new KitBuilderService(kitTemplateRepository, categoryRepository, estoqueUseCase);
        lenient().when(kitTemplateRepository.findById(7L)).thenReturn(Optional.of(kitMahal));
        stubProduct("BAG-01", "Bag Hemp", CAT_BAG, "40.00");
        stubProduct("SEDA-01", "Seda Alfafa", CAT_SEDA, "5.00");
        stubProduct("SEDA-02", "Seda King", CAT_SEDA, "6.00");
        stubProduct("ISQ-01", "Isqueiro Clipper", CAT_ISQUEIRO, "12.33");
    }

    private static Product product(String sku, String name, long categoryId, String price) {
        return Product.of(null, sku, name, "cat", true, List.of(), Pricing.of(null, null, new BigDecimal(price)),
                        ProductType.SIMPLES, false)
                .withCategory(categoryId, "cat")
                .withVisibleInPos(true)
                .withVisibleInMarketplace(true);
    }

    private void stubProduct(String sku, String name, long categoryId, String price) {
        lenient().when(estoqueUseCase.findProductBySku(sku)).thenReturn(product(sku, name, categoryId, price));
    }

    @Test
    void quote_sumsItemsAndProratesDiscountToTheCent() {
        KitQuote quote = service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "BAG-01"), new Pick(SEDA, "SEDA-01"), new Pick(ISQUEIRO, "ISQ-01"))),
                KitChannel.MARKETPLACE);

        assertThat(quote.subtotal()).isEqualByComparingTo("57.33");
        // 10% de 57,33 = 5,733 → 5,73
        assertThat(quote.discount()).isEqualByComparingTo("5.73");
        assertThat(quote.total()).isEqualByComparingTo("51.60");
        assertThat(quote.lines()).extracting(KitQuote.Line::sku).containsExactly("BAG-01", "SEDA-01", "ISQ-01");
        assertThat(quote.lines().stream().map(KitQuote.Line::discountAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(quote.discount());
    }

    @Test
    void quote_optionalStepMayBeSkipped() {
        KitQuote quote = service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "BAG-01"), new Pick(SEDA, "SEDA-01"))), KitChannel.PDV);

        assertThat(quote.lines()).hasSize(2);
        assertThat(quote.total()).isEqualByComparingTo("40.50");
    }

    @Test
    void quote_rejectsMissingRequiredStep() {
        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(new Pick(BAG, "BAG-01"))),
                KitChannel.MARKETPLACE))
                .isInstanceOf(InvalidKitSelectionException.class)
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_REQUIRED_STEP_MISSING);
    }

    @Test
    void quote_rejectsItemFromAnotherCategory() {
        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "ISQ-01"), new Pick(SEDA, "SEDA-01"))), KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_ITEM_NOT_IN_STEP_CATEGORY);
    }

    @Test
    void quote_rejectsMoreItemsThanStepAllows() {
        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "BAG-01"), new Pick(SEDA, "SEDA-01"), new Pick(SEDA, "SEDA-02"),
                new Pick(SEDA, "SEDA-01"))), KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_STEP_MAX_ITEMS_EXCEEDED);
    }

    @Test
    void quote_rejectsStepOfAnotherKit() {
        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(new Pick(999L, "BAG-01"))),
                KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_STEP_NOT_FOUND);
    }

    @Test
    void quote_rejectsEmptySelection() {
        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of()), KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_EMPTY);
    }

    @Test
    void quote_rejectsInactiveProductAndUnknownSku() {
        when(estoqueUseCase.findProductBySku("BAG-01"))
                .thenReturn(product("BAG-01", "Bag Hemp", CAT_BAG, "40.00").withActive(false));
        when(estoqueUseCase.findProductBySku("NAO-EXISTE")).thenThrow(new ProductNotFoundException("NAO-EXISTE"));

        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "BAG-01"), new Pick(SEDA, "SEDA-01"))), KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_ITEM_NOT_SELLABLE);
        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "NAO-EXISTE"), new Pick(SEDA, "SEDA-01"))), KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_ITEM_NOT_SELLABLE);
    }

    @Test
    void quote_rejectsProductHiddenFromChannel() {
        when(estoqueUseCase.findProductBySku("BAG-01"))
                .thenReturn(product("BAG-01", "Bag Hemp", CAT_BAG, "40.00").withVisibleInMarketplace(false));

        assertThatThrownBy(() -> service.quote(new KitSelection(7L, List.of(
                new Pick(BAG, "BAG-01"), new Pick(SEDA, "SEDA-01"))), KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_ITEM_NOT_SELLABLE);
    }

    @Test
    void quote_rejectsKitNotSellableInChannel() {
        KitTemplate soPdv = new KitTemplate(8L, "Kit Balcão", null, null, BigDecimal.ZERO, true, true, false,
                kitMahal.steps());
        when(kitTemplateRepository.findById(8L)).thenReturn(Optional.of(soPdv));

        assertThatThrownBy(() -> service.quote(new KitSelection(8L, List.of(new Pick(BAG, "BAG-01"))),
                KitChannel.MARKETPLACE))
                .extracting(e -> ((InvalidKitSelectionException) e).reason())
                .isEqualTo(Reason.KIT_NOT_AVAILABLE);
    }

    @Test
    void listStepOptions_expandsVariantsAndSkipsDraftsAndKits() {
        Product sedaComGrade = Product.of(null, "SEDA-G", "Seda Sabores", "cat", true,
                        List.of(ProductVariant.of(null, "SEDA-G-MENTA", List.of(), true),
                                ProductVariant.of(null, "SEDA-G-OFF", List.of(), false)),
                        Pricing.of(null, null, new BigDecimal("7.00")), ProductType.SIMPLES, false)
                .withCategory(CAT_SEDA, "cat").withVisibleInPos(true).withVisibleInMarketplace(true);
        Product rascunho = product("SEDA-R", "Rascunho", CAT_SEDA, "5.00").withStatus(ProductStatus.RASCUNHO);
        // EST-F054 — arquivado também não é opção de kit.
        Product arquivado = product("SEDA-A", "Excluído", CAT_SEDA, "5.00").withStatus(ProductStatus.ARQUIVADO);
        Product kit = product("SEDA-K", "Kit fixo", CAT_SEDA, "5.00").withType(ProductType.KIT);
        when(estoqueUseCase.listActivePricedProducts(0, KitBuilderService.MAX_OPTIONS_PER_STEP, null, CAT_SEDA, null))
                .thenReturn(new PageResult<>(List.of(sedaComGrade, rascunho, arquivado, kit,
                        product("SEDA-01", "Seda Alfafa", CAT_SEDA, "5.00")), 0, 100, 5L, 1));

        List<StepOption> options = service.listStepOptions(7L, SEDA, KitChannel.MARKETPLACE, null);

        assertThat(options).extracting(StepOption::sku).containsExactly("SEDA-G-MENTA", "SEDA-01");
        assertThat(options.get(0).productSku()).isEqualTo("SEDA-G");
        assertThat(options.get(0).available()).isNull();
    }

    @Test
    void createTemplate_rejectsDuplicateNameAndUnknownCategory() {
        when(kitTemplateRepository.findByNameIgnoreCase("Kit Mahal")).thenReturn(Optional.of(kitMahal));
        assertThatThrownBy(() -> service.createTemplate(kitMahal.withId(null)))
                .isInstanceOf(DuplicateKitTemplateNameException.class);

        KitTemplate novo = new KitTemplate(null, "Kit Novo", null, null, BigDecimal.ZERO, true, true, true,
                List.of(new KitTemplateStep(null, "Bag", 0, 404L, true, 1)));
        when(kitTemplateRepository.findByNameIgnoreCase("Kit Novo")).thenReturn(Optional.empty());
        when(categoryRepository.findById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createTemplate(novo)).isInstanceOf(CategoryNotFoundException.class);
        verify(kitTemplateRepository, never()).save(any());
    }

    @Test
    void updateTemplate_keepsOwnNameAndSaves() {
        when(kitTemplateRepository.findByNameIgnoreCase("Kit Mahal")).thenReturn(Optional.of(kitMahal));
        when(categoryRepository.findById(any())).thenReturn(Optional.of(mock(Category.class)));
        when(kitTemplateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        KitTemplate saved = service.updateTemplate(7L, kitMahal.withId(null));

        assertThat(saved.id()).isEqualTo(7L);
    }

    @Test
    void getSellableTemplate_hidesInactiveAs404() {
        KitTemplate inativo = new KitTemplate(9L, "Kit Off", null, null, BigDecimal.ZERO, false, true, true,
                kitMahal.steps());
        when(kitTemplateRepository.findById(9L)).thenReturn(Optional.of(inativo));

        assertThatThrownBy(() -> service.getSellableTemplate(9L, KitChannel.PDV))
                .isInstanceOf(KitTemplateNotFoundException.class);
    }

    @Test
    void template_rejectsDiscountOf100AndDuplicateStepNames() {
        assertThatThrownBy(() -> new KitTemplate(null, "X", null, null, new BigDecimal("100"), true, true, true,
                kitMahal.steps())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KitTemplate(null, "X", null, null, BigDecimal.ZERO, true, true, true,
                List.of(new KitTemplateStep(null, "Seda", 0, CAT_SEDA, true, 1),
                        new KitTemplateStep(null, "seda", 1, CAT_SEDA, false, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
