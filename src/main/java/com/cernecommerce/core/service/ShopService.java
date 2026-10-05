package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.DuplicateCustomerEmailException;
import com.cernecommerce.core.domain.exception.ecommerce.CartEmptyException;
import com.cernecommerce.core.domain.exception.ecommerce.CartItemNotFoundException;
import com.cernecommerce.core.domain.exception.estoque.InvalidKitSelectionException;
import com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.domain.model.ecommerce.CartItem;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.KitChannel;
import com.cernecommerce.core.domain.model.estoque.KitQuote;
import com.cernecommerce.core.domain.model.estoque.KitSelection;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.KitBuilderUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.ShopUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;
import com.cernecommerce.core.ports.out.ecommerce.PaymentGatewayPort;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ShopService implements ShopUseCase {

    private static final String MARKETPLACE_ORIGIN = "MARKETPLACE";

    private final CrmUseCase crmUseCase;
    private final UserUseCase userUseCase;
    private final EstoqueUseCase estoqueUseCase;
    private final CartRepository cartRepository;
    private final OrderRepository orderRepository;
    private final OrderUseCase orderUseCase;
    private final CashbackUseCase cashbackUseCase;
    private final PaymentGatewayPort paymentGatewayPort;
    private final OrderPaymentRepository orderPaymentRepository;
    private final EmailPort emailPort;
    private final KitBuilderUseCase kitBuilderUseCase;
    private final AfterCommitExecutor afterCommitExecutor;

    public ShopService(CrmUseCase crmUseCase, UserUseCase userUseCase, EstoqueUseCase estoqueUseCase,
            CartRepository cartRepository, OrderRepository orderRepository, OrderUseCase orderUseCase,
            CashbackUseCase cashbackUseCase, PaymentGatewayPort paymentGatewayPort,
            OrderPaymentRepository orderPaymentRepository, EmailPort emailPort,
            KitBuilderUseCase kitBuilderUseCase, AfterCommitExecutor afterCommitExecutor) {
        this.kitBuilderUseCase = kitBuilderUseCase;
        this.afterCommitExecutor = afterCommitExecutor;
        this.crmUseCase = crmUseCase;
        this.userUseCase = userUseCase;
        this.estoqueUseCase = estoqueUseCase;
        this.cartRepository = cartRepository;
        this.orderRepository = orderRepository;
        this.orderUseCase = orderUseCase;
        this.cashbackUseCase = cashbackUseCase;
        this.paymentGatewayPort = paymentGatewayPort;
        this.orderPaymentRepository = orderPaymentRepository;
        this.emailPort = emailPort;
    }

    @Override
    @Transactional
    public CustomerRegistration registerCustomer(String nome, String email, String contato, String rawPassword) {
        Customer customer = crmUseCase.createCustomer(nome, contato, email, null, MARKETPLACE_ORIGIN);
        try {
            User user = userUseCase.createCustomerAccount(email, rawPassword, customer.id());
            return new CustomerRegistration(customer, user);
        } catch (UsernameAlreadyExistsException | EmailAlreadyExistsException ex) {
            // Do ponto de vista do cliente só existe um campo, email — username=email é detalhe
            // interno. Normaliza para o mesmo erro que o CRM já usa para email duplicado, em vez
            // de vazar USERNAME_ALREADY_EXISTS/EMAIL_ALREADY_EXISTS, que não fazem sentido para
            // quem preencheu um formulário sem campo de username.
            throw new DuplicateCustomerEmailException(email);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CatalogItem> listCatalog(int page, int size, Boolean onSale, Long categoryId,
            String search) {
        Warehouse warehouse = estoqueUseCase.getDefaultWarehouse();
        PageResult<Product> products = estoqueUseCase.listActivePricedProducts(page, size, onSale, categoryId,
                search);
        return new PageResult<>(
                products.content().stream().map(p -> toCatalogItem(p, warehouse.code())).toList(),
                products.page(), products.size(), products.totalElements(), products.totalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> listCategories() {
        return estoqueUseCase.listActiveCategories();
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogItemDetail getCatalogItem(String sku) {
        Product product = estoqueUseCase.findProductBySku(sku);
        if (!product.active() || !product.pricing().isPriced()) {
            // Mesmo raciocínio de pedido de outro cliente (plano §5.4): o público não deve
            // conseguir distinguir "não existe" de "existe, mas não está à venda".
            throw new ProductNotFoundException(sku);
        }
        Warehouse warehouse = estoqueUseCase.getDefaultWarehouse();
        List<CatalogVariant> variants = product.variants().stream()
                .filter(ProductVariant::active)
                .map(v -> new CatalogVariant(v.sku(), v.attributes(), isAvailable(v.sku(), warehouse.code()),
                        product.effectivePricingFor(v.sku()).effectivePrice()))
                .toList();
        return new CatalogItemDetail(product.sku(), product.name(), product.category(),
                product.pricing().effectivePrice(), isAvailable(product.sku(), warehouse.code()), variants,
                product.imageUrl(), product.onSale(), product.pricing().originalPrice(), product.superPromo(),
                product.description(), product.videoUrl(), product.images());
    }

    private CatalogItem toCatalogItem(Product product, String warehouseCode) {
        return new CatalogItem(product.sku(), product.name(), product.category(),
                product.pricing().effectivePrice(), isAvailable(product.sku(), warehouseCode),
                product.imageUrl(), product.onSale(), product.pricing().originalPrice(), product.superPromo());
    }

    private boolean isAvailable(String sku, String warehouseCode) {
        return estoqueUseCase.getStockBalance(sku, warehouseCode).availableQuantity().signum() > 0;
    }

    // ---------------------------------------------------------------------------------------
    // Carrinho e checkout (ECM-F003 + ECM-C002, Fatia 9)
    // ---------------------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CartView getCart(String username) {
        Long customerId = requireCustomerId(username);
        Cart cart = cartRepository.findByCustomerId(customerId).orElse(Cart.empty(customerId));
        return toCartView(cart);
    }

    @Override
    @Transactional
    public CartView upsertCartItem(String username, String sku, BigDecimal quantity) {
        Long customerId = requireCustomerId(username);
        // Mesma checagem que o checkout fará de novo — o preço nunca é guardado no carrinho, então
        // não há como confiar que o que valia agora ainda vale na hora de fechar o pedido.
        requirePriced(sku);
        Cart cart = cartRepository.upsertItem(customerId, sku, quantity);
        return toCartView(cart);
    }

    @Override
    @Transactional
    public CartView removeCartItem(String username, String sku) {
        Long customerId = requireCustomerId(username);
        if (!cartRepository.removeItem(customerId, sku)) {
            throw new CartItemNotFoundException(sku);
        }
        Cart cart = cartRepository.findByCustomerId(customerId).orElse(Cart.empty(customerId));
        return toCartView(cart);
    }

    @Override
    @Transactional
    public CartView addKitToCart(String username, KitSelection selection) {
        Long customerId = requireCustomerId(username);
        KitQuote quote = kitBuilderUseCase.quote(selection, KitChannel.MARKETPLACE);
        String bundleId = UUID.randomUUID().toString();
        List<CartItem> items = quote.lines().stream()
                .map(line -> new CartItem(line.sku(), BigDecimal.ONE, bundleId, quote.template().id(), line.stepId()))
                .toList();
        return toCartView(cartRepository.addKitBundle(customerId, items));
    }

    @Override
    @Transactional
    public CartView removeKitFromCart(String username, String kitBundleId) {
        Long customerId = requireCustomerId(username);
        if (!cartRepository.removeKitBundle(customerId, kitBundleId)) {
            throw new CartItemNotFoundException(kitBundleId);
        }
        Cart cart = cartRepository.findByCustomerId(customerId).orElse(Cart.empty(customerId));
        return toCartView(cart);
    }

    /**
     * Agrupa as linhas de kit do carrinho por pacote, na ordem em que aparecem. A ordem das linhas
     * dentro do pacote é a ordem da escolha, e é ela que casa cada linha com a da cotação.
     */
    private static Map<String, List<CartItem>> kitBundles(Cart cart) {
        Map<String, List<CartItem>> bundles = new LinkedHashMap<>();
        for (CartItem item : cart.items()) {
            if (item.inKit()) {
                bundles.computeIfAbsent(item.kitBundleId(), k -> new ArrayList<>()).add(item);
            }
        }
        return bundles;
    }

    private static KitSelection toSelection(List<CartItem> bundle) {
        return new KitSelection(bundle.get(0).kitTemplateId(), bundle.stream()
                .map(i -> new KitSelection.Pick(i.kitStepId(), i.sku()))
                .toList());
    }

    @Override
    @Transactional
    public CheckoutResult checkout(String username) {
        Long customerId = requireCustomerId(username);
        Cart cart = cartRepository.findByCustomerId(customerId).orElse(Cart.empty(customerId));
        if (cart.isEmpty()) {
            throw new CartEmptyException();
        }
        Warehouse warehouse = estoqueUseCase.getDefaultWarehouse();

        // Preço, custo e taxa de cashback são resolvidos AGORA, do catálogo — nunca do que estava
        // no carrinho — mesmo caminho de PdvService.registerSale: fromCatalog recusa item sem
        // preço, e a taxa é carimbada aqui porque settleOnlineOrder/o webhook (que confirmam este
        // pedido mais tarde) não resolvem taxa nenhuma, só leem o que já foi carimbado.
        //
        // ECM-F008 — kit montável é RECOTADO aqui, pelo mesmo motivo: a cotação do carrinho não
        // prometeu nada. Pacote que deixou de fechar (item desativado, kit tirado de linha) recusa
        // o checkout inteiro com o código da regra, em vez de cobrar o kit sem o desconto em
        // silêncio. O desconto de cada linha é a parte rateada pela cotação.
        Map<String, Iterator<KitQuote.Line>> kitLines = new HashMap<>();
        for (Map.Entry<String, List<CartItem>> bundle : kitBundles(cart).entrySet()) {
            KitQuote quote = kitBuilderUseCase.quote(toSelection(bundle.getValue()), KitChannel.MARKETPLACE);
            kitLines.put(bundle.getKey(), quote.lines().iterator());
        }
        List<OrderItem> orderItems = new ArrayList<>(cart.items().size());
        for (CartItem cartItem : cart.items()) {
            EstoqueUseCase.CatalogSaleInfo saleInfo = estoqueUseCase.resolveSaleInfo(cartItem.sku());
            BigDecimal discount = cartItem.inKit()
                    ? kitLines.get(cartItem.kitBundleId()).next().discountAmount()
                    : BigDecimal.ZERO;
            OrderItem item = OrderItem.fromCatalog(cartItem.sku(), cartItem.quantity(),
                    saleInfo.pricing(), discount, saleInfo.productName());
            CashbackRate resolvedRate = cashbackUseCase.resolveApplicableRate(cartItem.sku());
            if (resolvedRate != null) {
                item = item.withCashbackPercent(resolvedRate.percent());
            }
            orderItems.add(item);
        }

        Order order = Order.openMarketplace(customerId, warehouse.code(), orderItems);
        Order saved = orderRepository.save(order);

        // Se a reserva de qualquer item falhar (InsufficientStockException), a transação inteira
        // desfaz o pedido recém-criado — não fica um AGUARDANDO_PAGAMENTO com reserva pela metade.
        for (OrderItem item : orderItems) {
            estoqueUseCase.reserveStock(item.sku(), warehouse.code(), item.quantity(),
                    Order.reservationOwnerReference(saved.id()), null, username);
        }

        // ECM-F004: orderNsu é sempre o nosso Order.id — o InfinitePay não devolve identificador
        // de transação na criação do link, só no webhook; a correlação de volta é por este valor.
        Customer customer = crmUseCase.findCustomerById(customerId);
        PaymentGatewayPort.CheckoutLink link = paymentGatewayPort.createCheckoutLink(
                saved.id().toString(), saved.netAmount(), "Pedido " + saved.id(),
                customer.nome(), customer.email());
        orderPaymentRepository.save(
                OrderPayment.pending(saved.id(), PaymentMethod.GATEWAY_PIX, saved.netAmount()));

        cartRepository.clear(customerId);
        if (customer.email() != null && !customer.email().isBlank()) {
            // Depois do commit: confirmação de um pedido que acabou revertido não pode sair.
            OrderEmailView view = OrderEmailView.of(saved, customer.nome());
            afterCommitExecutor.accumulate("shop.order-confirmation:" + saved.id(), saved, orders ->
                    emailPort.sendOrderConfirmation(customer.email(), view, link.checkoutUrl()));
        }
        return new CheckoutResult(saved, link.checkoutUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Order> listMyOrders(String username, int page, int size) {
        Long customerId = requireCustomerId(username);
        return orderUseCase.listOrders(null, null, customerId, null, null, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public Order getMyOrder(String username, Long orderId) {
        Long customerId = requireCustomerId(username);
        return requireOwnOrder(orderId, customerId);
    }

    @Override
    @Transactional
    public Order cancelMyOrder(String username, Long orderId, String reason) {
        Long customerId = requireCustomerId(username);
        requireOwnOrder(orderId, customerId);
        return orderUseCase.cancelOrder(orderId, reason, username);
    }

    /**
     * Pedido de outro cliente responde igual a pedido inexistente — 404, nunca 403 (plano §5.4):
     * 403 confirmaria a existência do recurso e transformaria a rota num oráculo de enumeração.
     */
    private Order requireOwnOrder(Long orderId, Long customerId) {
        Order order = orderUseCase.getOrder(orderId);
        if (!customerId.equals(order.customerId())) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    /**
     * Resolve o depósito padrão uma vez para o carrinho inteiro e propaga se não estiver
     * configurado — mesmo comportamento de {@link #listCatalog}, por consistência: se a vitrine
     * está fora do ar por falta de configuração, o carrinho também fica, em vez de mostrar
     * disponibilidade silenciosamente errada.
     */
    private CartView toCartView(Cart cart) {
        String warehouseCode = cart.isEmpty() ? null : estoqueUseCase.getDefaultWarehouse().code();
        // ECM-F008 — cada pacote é cotado para exibir o desconto. Pacote que não fecha mais aparece
        // sem desconto e indisponível, em vez de derrubar o GET do carrinho: é o checkout quem
        // recusa, e o cliente precisa enxergar o carrinho para remover o pacote.
        Map<String, Iterator<KitQuote.Line>> kitLines = new HashMap<>();
        for (Map.Entry<String, List<CartItem>> bundle : kitBundles(cart).entrySet()) {
            try {
                KitQuote quote = kitBuilderUseCase.quote(toSelection(bundle.getValue()), KitChannel.MARKETPLACE);
                kitLines.put(bundle.getKey(), quote.lines().iterator());
            } catch (InvalidKitSelectionException e) {
                // sem entrada no mapa = pacote inválido
            }
        }
        List<CartItemView> items = cart.items().stream()
                .map(item -> toCartItemView(item, warehouseCode, kitLines))
                .toList();
        BigDecimal gross = items.stream()
                .map(CartItemView::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discountTotal = items.stream()
                .map(CartItemView::discountAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartView(items, gross.subtract(discountTotal), cart.updatedAt(), discountTotal);
    }

    private CartItemView toCartItemView(CartItem cartItem, String warehouseCode,
            Map<String, Iterator<KitQuote.Line>> kitLines) {
        BigDecimal unitPrice = estoqueUseCase.findPricingBySku(cartItem.sku()).effectivePrice();
        BigDecimal subtotal = unitPrice.multiply(cartItem.quantity());
        boolean available = isAvailable(cartItem.sku(), warehouseCode);
        if (!cartItem.inKit()) {
            return new CartItemView(cartItem.sku(), cartItem.quantity(), unitPrice, subtotal, available);
        }
        Iterator<KitQuote.Line> lines = kitLines.get(cartItem.kitBundleId());
        BigDecimal discount = lines == null ? BigDecimal.ZERO : lines.next().discountAmount();
        return new CartItemView(cartItem.sku(), cartItem.quantity(), unitPrice, subtotal, available && lines != null,
                cartItem.kitBundleId(), cartItem.kitTemplateId(), cartItem.kitStepId(), discount);
    }

    /** Mesma validação que {@link OrderItem#fromCatalog} faria no checkout — só descarta o resultado. */
    private void requirePriced(String sku) {
        OrderItem.fromCatalog(sku, BigDecimal.ONE, estoqueUseCase.findPricingBySku(sku), BigDecimal.ZERO, null);
    }

    private Long requireCustomerId(String username) {
        User user = userUseCase.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
        return user.getCustomerId();
    }
}
