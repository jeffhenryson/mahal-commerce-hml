package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException;
import com.cernecommerce.core.domain.exception.ecommerce.CartEmptyException;
import com.cernecommerce.core.domain.exception.ecommerce.CartItemNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.DefaultWarehouseNotConfiguredException;
import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.domain.model.ecommerce.CartItem;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.KitTemplate;
import com.cernecommerce.core.domain.model.estoque.KitTemplateStep;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.ShopUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.domain.exception.pagamento.PaymentGatewayException;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopServiceTest {

    @Mock CrmUseCase crmUseCase;
    @Mock UserUseCase userUseCase;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CartRepository cartRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderUseCase orderUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock PaymentGatewayPort paymentGatewayPort;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EmailPort emailPort;
    @Mock KitBuilderUseCase kitBuilderUseCase;

    ShopService shopService;

    private static final Warehouse WAREHOUSE =
            Warehouse.of(1L, "LOJA-01", "Loja Centro", WarehouseType.LOJA_FISICA, true);
    private static final String USERNAME = "maria@x.com";
    private static final Long CUSTOMER_ID = 7L;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(crmUseCase, userUseCase, estoqueUseCase, cartRepository,
                orderRepository, orderUseCase, cashbackUseCase, paymentGatewayPort, orderPaymentRepository,
                emailPort, kitBuilderUseCase, immediateExecutor);
    }

    /** Sem transação real nestes testes, o executor despacha na hora (como a implementação sem sincronização). */
    private final AfterCommitExecutor immediateExecutor = new AfterCommitExecutor() {
        @Override
        public <T> void accumulate(String key, T item, java.util.function.Consumer<java.util.List<T>> flush) {
            flush.accept(java.util.List.of(item));
        }
    };

    private void stubAuthenticatedCustomer() {
        User user = User.customer(USERNAME, "hashed", USERNAME, CUSTOMER_ID, java.util.Set.of(new Role("ROLE_CUSTOMER")));
        lenient().when(userUseCase.findByUsername(USERNAME)).thenReturn(Optional.of(user));
    }

    private void stubCustomerLookup() {
        Customer customer = Customer.of(CUSTOMER_ID, "Maria", "11999990000", USERNAME, null,
                "MARKETPLACE", Instant.now(), com.cernecommerce.core.domain.model.crm.CustomerStage.NOVO_LEAD);
        lenient().when(crmUseCase.findCustomerById(CUSTOMER_ID)).thenReturn(customer);
    }

    @Test
    void registerCustomer_createsCustomerAndLinkedUserAccount() {
        Customer customer = Customer.of(7L, "Maria", "11999990000", "maria@x.com", null,
                "MARKETPLACE", java.time.Instant.now(), com.cernecommerce.core.domain.model.crm.CustomerStage.NOVO_LEAD);
        when(crmUseCase.createCustomer("Maria", "11999990000", "maria@x.com", null, "MARKETPLACE"))
                .thenReturn(customer);
        User user = User.customer("maria@x.com", "hashed", "maria@x.com", 7L, java.util.Set.of(new Role("ROLE_CUSTOMER")));
        when(userUseCase.createCustomerAccount("maria@x.com", "S3nha@forte", 7L)).thenReturn(user);

        ShopUseCase.CustomerRegistration result = shopService.registerCustomer(
                "Maria", "maria@x.com", "11999990000", "S3nha@forte");

        assertThat(result.customer().id()).isEqualTo(7L);
        assertThat(result.user().isCustomer()).isTrue();
        verify(userUseCase).createCustomerAccount("maria@x.com", "S3nha@forte", 7L);
    }

    @Test
    void registerCustomer_translatesUsernameCollisionIntoDuplicateCustomerEmail() {
        Customer customer = Customer.of(7L, "Maria", "11999990000", "maria@x.com", null,
                "MARKETPLACE", java.time.Instant.now(), com.cernecommerce.core.domain.model.crm.CustomerStage.NOVO_LEAD);
        when(crmUseCase.createCustomer(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(customer);
        when(userUseCase.createCustomerAccount(anyString(), anyString(), any()))
                .thenThrow(new UsernameAlreadyExistsException("maria@x.com"));

        assertThatThrownBy(() -> shopService.registerCustomer("Maria", "maria@x.com", "11999990000", "S3nha@forte"))
                .isInstanceOf(DuplicateCustomerEmailException.class);
    }

    @Test
    void registerCustomer_translatesEmailCollisionIntoDuplicateCustomerEmail() {
        Customer customer = Customer.of(7L, "Maria", "11999990000", "maria@x.com", null,
                "MARKETPLACE", java.time.Instant.now(), com.cernecommerce.core.domain.model.crm.CustomerStage.NOVO_LEAD);
        when(crmUseCase.createCustomer(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(customer);
        when(userUseCase.createCustomerAccount(anyString(), anyString(), any()))
                .thenThrow(new EmailAlreadyExistsException("maria@x.com"));

        assertThatThrownBy(() -> shopService.registerCustomer("Maria", "maria@x.com", "11999990000", "S3nha@forte"))
                .isInstanceOf(DuplicateCustomerEmailException.class);
    }

    @Test
    void registerCustomer_propagatesDuplicateEmailFromCrmDirectly() {
        when(crmUseCase.createCustomer(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new DuplicateCustomerEmailException("maria@x.com"));

        assertThatThrownBy(() -> shopService.registerCustomer("Maria", "maria@x.com", "11999990000", "S3nha@forte"))
                .isInstanceOf(DuplicateCustomerEmailException.class);
        verifyNoInteractions(userUseCase);
    }

    @Test
    void listCatalog_returnsActivePricedProductsWithAvailability() {
        Product product = Product.of(1L, "ESS-001", "Essência Maçã", "essencia", true, List.of(),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.listActivePricedProducts(0, 20, null, null, null))
                .thenReturn(new PageResult<>(List.of(product), 0, 20, 1L, 1));
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));

        PageResult<ShopUseCase.CatalogItem> result = shopService.listCatalog(0, 20, null);

        assertThat(result.content()).hasSize(1);
        ShopUseCase.CatalogItem item = result.content().get(0);
        assertThat(item.sku()).isEqualTo("ESS-001");
        assertThat(item.price()).isEqualByComparingTo("30.00");
        assertThat(item.available()).isTrue();
    }

    @Test
    void listCatalog_marksOutOfStockItemUnavailable() {
        Product product = Product.of(1L, "ESS-001", "Essência Maçã", "essencia", true, List.of(),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.listActivePricedProducts(0, 20, null, null, null))
                .thenReturn(new PageResult<>(List.of(product), 0, 20, 1L, 1));
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.ZERO, BigDecimal.ZERO, 0L));

        PageResult<ShopUseCase.CatalogItem> result = shopService.listCatalog(0, 20, null);

        assertThat(result.content().get(0).available()).isFalse();
    }

    @Test
    void listCatalog_propagatesDefaultWarehouseNotConfiguredWithoutQueryingProducts() {
        when(estoqueUseCase.getDefaultWarehouse()).thenThrow(new DefaultWarehouseNotConfiguredException());

        assertThatThrownBy(() -> shopService.listCatalog(0, 20, null))
                .isInstanceOf(DefaultWarehouseNotConfiguredException.class);
        verify(estoqueUseCase, never()).listActivePricedProducts(anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void getCatalogItem_returnsDetailWithOnlyActiveVariants() {
        ProductVariant activeVariant = ProductVariant.of(1L, "ESS-001-MACA",
                List.of(new ProductAttribute("sabor", "Maçã")), true);
        ProductVariant inactiveVariant = ProductVariant.of(2L, "ESS-001-UVA",
                List.of(new ProductAttribute("sabor", "Uva")), false);
        Product product = Product.of(1L, "ESS-001", "Essência", "essencia", true,
                List.of(activeVariant, inactiveVariant),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        when(estoqueUseCase.findProductBySku("ESS-001")).thenReturn(product);
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));
        when(estoqueUseCase.getStockBalance("ESS-001-MACA", "LOJA-01"))
                .thenReturn(StockBalance.of(2L, "ESS-001-MACA", 1L, BigDecimal.ZERO, BigDecimal.ZERO, 0L));

        ShopUseCase.CatalogItemDetail detail = shopService.getCatalogItem("ESS-001");

        assertThat(detail.price()).isEqualByComparingTo("30.00");
        assertThat(detail.available()).isTrue();
        assertThat(detail.variants()).hasSize(1);
        assertThat(detail.variants().get(0).sku()).isEqualTo("ESS-001-MACA");
        assertThat(detail.variants().get(0).available()).isFalse();
    }

    @Test
    void getCatalogItem_throwsProductNotFoundWhenInactive() {
        Product product = Product.of(1L, "ESS-001", "Essência", "essencia", false, List.of(),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        when(estoqueUseCase.findProductBySku("ESS-001")).thenReturn(product);

        assertThatThrownBy(() -> shopService.getCatalogItem("ESS-001"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(estoqueUseCase, never()).getDefaultWarehouse();
    }

    @Test
    void getCatalogItem_throwsProductNotFoundWhenNotPriced() {
        Product product = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(), Pricing.empty());
        when(estoqueUseCase.findProductBySku("ESS-001")).thenReturn(product);

        assertThatThrownBy(() -> shopService.getCatalogItem("ESS-001"))
                .isInstanceOf(ProductNotFoundException.class);
        verify(estoqueUseCase, never()).getDefaultWarehouse();
    }

    // ---------- Campos de marketing: originalPrice, superPromo, description, videoUrl, images ----------

    @Test
    void listCatalog_expoeOriginalPriceESuperPromoNoItemResumido() {
        Product product = Product.of(1L, "ESS-001", "Essência Maçã", "essencia", true, List.of(),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00"), new BigDecimal("40.00")),
                ProductType.SIMPLES, false, null, null, false, true, "Descrição", "http://video.mp4",
                List.of("http://img1.png"));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.listActivePricedProducts(0, 20, null, null, null))
                .thenReturn(new PageResult<>(List.of(product), 0, 20, 1L, 1));
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));

        ShopUseCase.CatalogItem item = shopService.listCatalog(0, 20, null).content().get(0);

        assertThat(item.originalPrice()).isEqualByComparingTo("40.00");
        assertThat(item.superPromo()).isTrue();
    }

    @Test
    void getCatalogItem_expoeOsCincoCamposDeMarketingNoDetalhe() {
        Product product = Product.of(1L, "ESS-001", "Essência", "essencia", true, List.of(),
                Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00"), new BigDecimal("40.00")),
                ProductType.SIMPLES, false, null, null, false, true, "Descrição longa", "http://video.mp4",
                List.of("http://img1.png", "http://img2.png"));
        when(estoqueUseCase.findProductBySku("ESS-001")).thenReturn(product);
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));

        ShopUseCase.CatalogItemDetail detail = shopService.getCatalogItem("ESS-001");

        assertThat(detail.originalPrice()).isEqualByComparingTo("40.00");
        assertThat(detail.superPromo()).isTrue();
        assertThat(detail.description()).isEqualTo("Descrição longa");
        assertThat(detail.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(detail.images()).containsExactly("http://img1.png", "http://img2.png");
    }

    // ---------------------------------------------------------------------------------------
    // Carrinho e checkout (ECM-F003 + ECM-C002, Fatia 9)
    // ---------------------------------------------------------------------------------------

    @Test
    void getCart_returnsEmptyViewWhenNeverUsed() {
        stubAuthenticatedCustomer();
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        ShopUseCase.CartView view = shopService.getCart(USERNAME);

        assertThat(view.items()).isEmpty();
        assertThat(view.total()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(estoqueUseCase, never()).getDefaultWarehouse();
    }

    @Test
    void getCart_returnsEnrichedItemsWithPriceAndAvailability() {
        stubAuthenticatedCustomer();
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(new CartItem("ESS-001", new BigDecimal("2"))), Instant.now());
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.findPricingBySku("ESS-001"))
                .thenReturn(Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));

        ShopUseCase.CartView view = shopService.getCart(USERNAME);

        assertThat(view.items()).hasSize(1);
        ShopUseCase.CartItemView item = view.items().get(0);
        assertThat(item.unitPrice()).isEqualByComparingTo("30.00");
        assertThat(item.subtotal()).isEqualByComparingTo("60.00");
        assertThat(item.available()).isTrue();
        assertThat(view.total()).isEqualByComparingTo("60.00");
    }

    @Test
    void upsertCartItem_validatesPricingBeforeWritingAndReturnsUpdatedView() {
        stubAuthenticatedCustomer();
        when(estoqueUseCase.findPricingBySku("ESS-001"))
                .thenReturn(Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")));
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(new CartItem("ESS-001", new BigDecimal("3"))), Instant.now());
        when(cartRepository.upsertItem(CUSTOMER_ID, "ESS-001", new BigDecimal("3"))).thenReturn(cart);
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.getStockBalance("ESS-001", "LOJA-01"))
                .thenReturn(StockBalance.of(1L, "ESS-001", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));

        ShopUseCase.CartView view = shopService.upsertCartItem(USERNAME, "ESS-001", new BigDecimal("3"));

        assertThat(view.items()).hasSize(1);
        verify(cartRepository).upsertItem(CUSTOMER_ID, "ESS-001", new BigDecimal("3"));
    }

    @Test
    void upsertCartItem_throwsProductNotPricedAndNeverWritesToCart() {
        stubAuthenticatedCustomer();
        when(estoqueUseCase.findPricingBySku("ESS-002")).thenReturn(Pricing.empty());

        assertThatThrownBy(() -> shopService.upsertCartItem(USERNAME, "ESS-002", BigDecimal.ONE))
                .isInstanceOf(ProductNotPricedException.class);
        verify(cartRepository, never()).upsertItem(any(), any(), any());
    }

    @Test
    void upsertCartItem_throwsProductNotFoundForUnknownSku() {
        stubAuthenticatedCustomer();
        when(estoqueUseCase.findPricingBySku("GHOST")).thenThrow(new ProductNotFoundException("GHOST"));

        assertThatThrownBy(() -> shopService.upsertCartItem(USERNAME, "GHOST", BigDecimal.ONE))
                .isInstanceOf(ProductNotFoundException.class);
        verify(cartRepository, never()).upsertItem(any(), any(), any());
    }

    @Test
    void removeCartItem_throwsCartItemNotFoundWhenAbsent() {
        stubAuthenticatedCustomer();
        when(cartRepository.removeItem(CUSTOMER_ID, "ESS-001")).thenReturn(false);

        assertThatThrownBy(() -> shopService.removeCartItem(USERNAME, "ESS-001"))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void removeCartItem_removesAndReturnsRemainingView() {
        stubAuthenticatedCustomer();
        when(cartRepository.removeItem(CUSTOMER_ID, "ESS-001")).thenReturn(true);
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        ShopUseCase.CartView view = shopService.removeCartItem(USERNAME, "ESS-001");

        assertThat(view.items()).isEmpty();
    }

    @Test
    void checkout_throwsCartEmptyAndNeverTouchesWarehouseOrOrders() {
        stubAuthenticatedCustomer();
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shopService.checkout(USERNAME))
                .isInstanceOf(CartEmptyException.class);
        verify(estoqueUseCase, never()).getDefaultWarehouse();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void checkout_createsMarketplaceOrderReservesStockCreatesChargeAndClearsCart() {
        stubAuthenticatedCustomer();
        stubCustomerLookup();
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(
                new CartItem("ESS-001", new BigDecimal("2")),
                new CartItem("CARV-001", new BigDecimal("1"))), Instant.now());
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.resolveSaleInfo("ESS-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Essência", Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00"))));
        when(estoqueUseCase.resolveSaleInfo("CARV-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Carvão", Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("20.00"))));
        when(cashbackUseCase.resolveApplicableRate(anyString())).thenReturn(
                new CashbackRate(1L, CashbackScope.GLOBAL, null, new BigDecimal("3.00"), true,
                        Instant.now(), null, Instant.now()));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 99L));
        when(paymentGatewayPort.createCheckoutLink(eq("99"), any(), any(), any(), any()))
                .thenReturn(new PaymentGatewayPort.CheckoutLink("https://checkout.infinitepay.io/loja?lenc=abc"));

        ShopUseCase.CheckoutResult result = shopService.checkout(USERNAME);

        assertThat(result.order().id()).isEqualTo(99L);
        assertThat(result.order().channel()).isEqualTo(SalesChannel.MARKETPLACE);
        assertThat(result.order().status()).isEqualTo(OrderStatus.AGUARDANDO_PAGAMENTO);
        assertThat(result.order().customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.infinitepay.io/loja?lenc=abc");

        verify(estoqueUseCase).reserveStock(eq("ESS-001"), eq("LOJA-01"), eq(new BigDecimal("2")),
                eq("ORDER:99"), isNull(), eq(USERNAME));
        verify(estoqueUseCase).reserveStock(eq("CARV-001"), eq("LOJA-01"), eq(new BigDecimal("1")),
                eq("ORDER:99"), isNull(), eq(USERNAME));
        // orderNsu é sempre o Order.id, nunca uma referência inventada — é o que correlaciona a
        // notificação do webhook de volta ao pedido, já que o InfinitePay só revela transaction_nsu lá.
        verify(paymentGatewayPort).createCheckoutLink(eq("99"), eq(result.order().netAmount()), any(), any(), any());
        ArgumentCaptor<OrderPayment> paymentCaptor = ArgumentCaptor.forClass(OrderPayment.class);
        verify(orderPaymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentCaptor.getValue().method()).isEqualTo(PaymentMethod.GATEWAY_PIX);
        assertThat(paymentCaptor.getValue().gatewayRef()).isNull();
        verify(cartRepository).clear(CUSTOMER_ID);
        ArgumentCaptor<OrderEmailView> viewCaptor = ArgumentCaptor.forClass(OrderEmailView.class);
        verify(emailPort).sendOrderConfirmation(eq(USERNAME), viewCaptor.capture(),
                eq("https://checkout.infinitepay.io/loja?lenc=abc"));
        assertThat(viewCaptor.getValue().customerName()).isEqualTo("Maria");
        assertThat(viewCaptor.getValue().orderReference()).isEqualTo("#99");
        assertThat(viewCaptor.getValue().itemCount()).isEqualTo(2);
        assertThat(viewCaptor.getValue().total()).isEqualByComparingTo(result.order().totalPayable());
    }

    // ---------------------------------------------------------------------------------------
    // ECM-F008 — kit montável no carrinho
    // ---------------------------------------------------------------------------------------

    private static final KitTemplate KIT_MAHAL =
            new KitTemplate(7L, "Kit Mahal", null, null,
                    new BigDecimal("10"), true, true, true, List.of(
                            new KitTemplateStep(10L, "Bag", 0, 1L, true, 1),
                            new KitTemplateStep(20L, "Seda", 1, 2L, true, 1)));

    private static KitQuote kitQuote() {
        return new KitQuote(KIT_MAHAL, List.of(
                new KitQuote.Line(10L, "Bag", "BAG-01", "Bag",
                        new BigDecimal("40.00"), new BigDecimal("4.00")),
                new KitQuote.Line(20L, "Seda", "SEDA-01", "Seda",
                        new BigDecimal("10.00"), new BigDecimal("1.00"))),
                new BigDecimal("50.00"), new BigDecimal("5.00"), new BigDecimal("45.00"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void addKitToCart_writesOneBundleWithStepOfEachLine() {
        stubAuthenticatedCustomer();
        var selection = new KitSelection(7L, List.of(
                new KitSelection.Pick(10L, "BAG-01"),
                new KitSelection.Pick(20L, "SEDA-01")));
        when(kitBuilderUseCase.quote(selection, KitChannel.MARKETPLACE))
                .thenReturn(kitQuote());
        when(cartRepository.addKitBundle(eq(CUSTOMER_ID), anyList()))
                .thenAnswer(inv -> Cart.of(1L, CUSTOMER_ID, inv.getArgument(1), Instant.now()));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.findPricingBySku("BAG-01")).thenReturn(Pricing.of(null, null, new BigDecimal("40.00")));
        when(estoqueUseCase.findPricingBySku("SEDA-01")).thenReturn(Pricing.of(null, null, new BigDecimal("10.00")));
        when(estoqueUseCase.getStockBalance(anyString(), eq("LOJA-01")))
                .thenReturn(StockBalance.of(1L, "X", 1L, BigDecimal.TEN, BigDecimal.ZERO, 0L));

        ShopUseCase.CartView view = shopService.addKitToCart(USERNAME, selection);

        ArgumentCaptor<List<CartItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(cartRepository).addKitBundle(eq(CUSTOMER_ID), captor.capture());
        List<CartItem> written = captor.getValue();
        assertThat(written).extracting(CartItem::sku, CartItem::kitStepId, CartItem::kitTemplateId)
                .containsExactly(tuple("BAG-01", 10L, 7L), tuple("SEDA-01", 20L, 7L));
        assertThat(written.get(0).kitBundleId()).isNotNull().isEqualTo(written.get(1).kitBundleId());
        // Carrinho mostra o líquido: 50 cheio − 5 do kit.
        assertThat(view.discountTotal()).isEqualByComparingTo("5.00");
        assertThat(view.total()).isEqualByComparingTo("45.00");
    }

    @Test
    void removeKitFromCart_throwsWhenBundleIsNotInCart() {
        stubAuthenticatedCustomer();
        when(cartRepository.removeKitBundle(CUSTOMER_ID, "nao-existe")).thenReturn(false);

        assertThatThrownBy(() -> shopService.removeKitFromCart(USERNAME, "nao-existe"))
                .isInstanceOf(CartItemNotFoundException.class);
    }

    @Test
    void checkout_requotesKitAndCarriesProratedDiscountToOrderItems() {
        stubAuthenticatedCustomer();
        stubCustomerLookup();
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(
                new CartItem("ESS-001", BigDecimal.ONE),
                new CartItem("BAG-01", BigDecimal.ONE, "b-1", 7L, 10L),
                new CartItem("SEDA-01", BigDecimal.ONE, "b-1", 7L, 20L)), Instant.now());
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(kitBuilderUseCase.quote(new KitSelection(7L, List.of(
                        new KitSelection.Pick(10L, "BAG-01"),
                        new KitSelection.Pick(20L, "SEDA-01"))),
                KitChannel.MARKETPLACE)).thenReturn(kitQuote());
        when(estoqueUseCase.resolveSaleInfo("ESS-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Essência", Pricing.of(null, null, new BigDecimal("30.00"))));
        when(estoqueUseCase.resolveSaleInfo("BAG-01")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Bag", Pricing.of(null, null, new BigDecimal("40.00"))));
        when(estoqueUseCase.resolveSaleInfo("SEDA-01")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Seda", Pricing.of(null, null, new BigDecimal("10.00"))));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 99L));
        when(paymentGatewayPort.createCheckoutLink(eq("99"), any(), any(), any(), any()))
                .thenReturn(new PaymentGatewayPort.CheckoutLink("https://checkout"));

        ShopUseCase.CheckoutResult result = shopService.checkout(USERNAME);

        assertThat(result.order().items())
                .extracting(i -> i.sku(), i -> i.discountAmount())
                .containsExactly(tuple("ESS-001", BigDecimal.ZERO), tuple("BAG-01", new BigDecimal("4.00")),
                        tuple("SEDA-01", new BigDecimal("1.00")));
        assertThat(result.order().netAmount()).isEqualByComparingTo("75.00");
        verify(estoqueUseCase).reserveStock(eq("BAG-01"), eq("LOJA-01"), eq(BigDecimal.ONE), eq("ORDER:99"),
                isNull(), eq(USERNAME));
    }

    @Test
    void checkout_refusesWhenKitNoLongerQuotes() {
        stubAuthenticatedCustomer();
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(
                new CartItem("BAG-01", BigDecimal.ONE, "b-1", 7L, 10L)), Instant.now());
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(kitBuilderUseCase.quote(any(), any())).thenThrow(
                new InvalidKitSelectionException(
                        InvalidKitSelectionException.Reason.KIT_REQUIRED_STEP_MISSING,
                        "Passo obrigatório sem escolha: Seda"));

        assertThatThrownBy(() -> shopService.checkout(USERNAME))
                .isInstanceOf(InvalidKitSelectionException.class);
        verify(orderRepository, never()).save(any());
        verify(cartRepository, never()).clear(any());
    }

    @Test
    void checkout_propagatesInsufficientStockAndNeverClearsCart() {
        stubAuthenticatedCustomer();
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(new CartItem("ESS-001", new BigDecimal("2"))), Instant.now());
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.resolveSaleInfo("ESS-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Essência", Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00"))));
        when(cashbackUseCase.resolveApplicableRate(anyString())).thenReturn(null);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 99L));
        when(estoqueUseCase.reserveStock(eq("ESS-001"), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientStockException("ESS-001", 1L, BigDecimal.ZERO, new BigDecimal("2")));

        assertThatThrownBy(() -> shopService.checkout(USERNAME))
                .isInstanceOf(InsufficientStockException.class);
        verify(paymentGatewayPort, never()).createCheckoutLink(any(), any(), any(), any(), any());
        verify(cartRepository, never()).clear(any());
        verifyNoInteractions(emailPort);
    }

    @Test
    void checkout_propagatesPaymentGatewayExceptionAndNeverClearsCart() {
        stubAuthenticatedCustomer();
        stubCustomerLookup();
        Cart cart = Cart.of(1L, CUSTOMER_ID, List.of(new CartItem("ESS-001", new BigDecimal("2"))), Instant.now());
        when(cartRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(Optional.of(cart));
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(WAREHOUSE);
        when(estoqueUseCase.resolveSaleInfo("ESS-001")).thenReturn(new EstoqueUseCase.CatalogSaleInfo(
                "Essência", Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00"))));
        when(cashbackUseCase.resolveApplicableRate(anyString())).thenReturn(null);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 99L));
        when(paymentGatewayPort.createCheckoutLink(any(), any(), any(), any(), any()))
                .thenThrow(new PaymentGatewayException("falha de rede", new RuntimeException()));

        assertThatThrownBy(() -> shopService.checkout(USERNAME))
                .isInstanceOf(PaymentGatewayException.class);
        verify(orderPaymentRepository, never()).save(any());
        verify(cartRepository, never()).clear(any());
        verifyNoInteractions(emailPort);
    }

    @Test
    void listMyOrders_delegatesWithCustomerIdFilterOnly() {
        stubAuthenticatedCustomer();
        when(orderUseCase.listOrders(null, null, CUSTOMER_ID, null, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        shopService.listMyOrders(USERNAME, 0, 20);

        verify(orderUseCase).listOrders(null, null, CUSTOMER_ID, null, null, 0, 20);
    }

    @Test
    void getMyOrder_returnsOwnOrder() {
        stubAuthenticatedCustomer();
        Order order = marketplaceOrder(10L, CUSTOMER_ID);
        when(orderUseCase.getOrder(10L)).thenReturn(order);

        Order result = shopService.getMyOrder(USERNAME, 10L);

        assertThat(result.id()).isEqualTo(10L);
    }

    @Test
    void getMyOrder_throwsOrderNotFoundForForeignOrder() {
        stubAuthenticatedCustomer();
        Order foreignOrder = marketplaceOrder(10L, 999L);
        when(orderUseCase.getOrder(10L)).thenReturn(foreignOrder);

        assertThatThrownBy(() -> shopService.getMyOrder(USERNAME, 10L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelMyOrder_throwsOrderNotFoundForForeignOrderWithoutCancelling() {
        stubAuthenticatedCustomer();
        Order foreignOrder = marketplaceOrder(10L, 999L);
        when(orderUseCase.getOrder(10L)).thenReturn(foreignOrder);

        assertThatThrownBy(() -> shopService.cancelMyOrder(USERNAME, 10L, "desisti"))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderUseCase, never()).cancelOrder(any(), any(), any());
    }

    @Test
    void cancelMyOrder_delegatesToOrderUseCaseWhenOwned() {
        stubAuthenticatedCustomer();
        Order order = marketplaceOrder(10L, CUSTOMER_ID);
        when(orderUseCase.getOrder(10L)).thenReturn(order);
        when(orderUseCase.cancelOrder(10L, "desisti", USERNAME)).thenReturn(order);

        shopService.cancelMyOrder(USERNAME, 10L, "desisti");

        verify(orderUseCase).cancelOrder(10L, "desisti", USERNAME);
    }

    private Order marketplaceOrder(Long id, Long customerId) {
        com.cernecommerce.core.domain.model.pedido.OrderItem item =
                com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("ESS-001", BigDecimal.ONE,
                        Pricing.of(new BigDecimal("15.00"), null, new BigDecimal("30.00")), BigDecimal.ZERO);
        Order order = Order.openMarketplace(customerId, "LOJA-01", List.of(item));
        return withId(order, id);
    }

    private Order withId(Order order, Long id) {
        return Order.of(id, order.orderNumber(), order.channel(), order.status(), order.customerId(),
                order.sessionId(), order.warehouseCode(), order.items(), order.grossAmount(),
                order.discountAmount(), order.cashbackRedeemed(), order.netAmount(), order.changeAmount(),
                order.cancelReason(), order.createdAt(), order.paidAt(), order.concludedAt(),
                order.cancelledAt(), order.refundedAt(), order.version());
    }

    @Test
    void getCatalogItem_precoDaVariacaoSaiResolvidoProprioOuHerdado() {
        // A vitrine não pode reimplementar a precedência de EST-F020 — se reimplementasse,
        // divergiria do que o PDV cobra.
        Product produto = Product.of(1L, "ESS-001", "Essência", "essencia", true,
                List.of(ProductVariant.of(9L, "ESS-001-50G", List.of(), true),
                        ProductVariant.of(10L, "ESS-001-100G", List.of(), true,
                                Pricing.of(null, null, new BigDecimal("99.90")))),
                Pricing.of(null, null, new BigDecimal("30.00")));
        when(estoqueUseCase.findProductBySku("ESS-001")).thenReturn(produto);
        when(estoqueUseCase.getDefaultWarehouse()).thenReturn(
                Warehouse.of(1L, "PRINCIPAL", "Principal", WarehouseType.LOJA_FISICA, true));
        when(estoqueUseCase.getStockBalance(anyString(), anyString()))
                .thenReturn(StockBalance.of(1L, "X", 1L, new BigDecimal("5"), BigDecimal.ZERO, 0L));

        ShopUseCase.CatalogItemDetail detail = shopService.getCatalogItem("ESS-001");

        assertThat(detail.variants())
                .extracting(ShopUseCase.CatalogVariant::sku, v -> v.price().stripTrailingZeros())
                .containsExactly(
                        tuple("ESS-001-50G", new BigDecimal("30.00").stripTrailingZeros()),
                        tuple("ESS-001-100G", new BigDecimal("99.90").stripTrailingZeros()));
    }


    @Test
    void listCategories_delegaAoEstoque() {
        when(estoqueUseCase.listActiveCategories()).thenReturn(List.of(
                com.cernecommerce.core.domain.model.estoque.Category.of(1L, "Promoções", true, 0, true)));

        assertThat(shopService.listCategories())
                .extracting(com.cernecommerce.core.domain.model.estoque.Category::name)
                .containsExactly("Promoções");
    }
}
