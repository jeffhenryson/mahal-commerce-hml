package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.converter.ShopCartDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CartItemRequest;
import com.cernecommerce.adapter.in.dtos.request.OrderCancelRequest;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCartResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ShopUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Superfície <b>autenticada</b> do marketplace (ECM-F003, Fatia 9) — carrinho, checkout e pedidos
 * do próprio cliente. Separada de {@link ShopController} de propósito: aquele é público por
 * natureza (cadastro, catálogo); este exige {@code SHOP_CART_OWN}/{@code SHOP_ORDER_OWN} de
 * verdade via {@code @PreAuthorize}, com a propriedade sempre resolvida aqui a partir do
 * {@link Authentication} — nunca de um {@code customerId} vindo do request.
 *
 * <p>Pedido de outro cliente responde <b>404, não 403</b>: 403 confirmaria a existência do
 * recurso e transformaria a rota num oráculo de enumeração (plano-pdv-marketplace.md §5.4).</p>
 */
@RestController
@RequestMapping("/shop")
@Tag(name = "Shop — Conta", description = "Carrinho, checkout e pedidos do cliente autenticado")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ShopAccountController {

    private final ShopUseCase shopUseCase;
    private final ShopCartDTOConverter cartConverter;
    private final OrderDTOConverter orderConverter;
    private final ApplicationEventPublisher publisher;

    public ShopAccountController(ShopUseCase shopUseCase, ShopCartDTOConverter cartConverter,
            OrderDTOConverter orderConverter, ApplicationEventPublisher publisher) {
        this.shopUseCase = shopUseCase;
        this.cartConverter = cartConverter;
        this.orderConverter = orderConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Carrinho do cliente autenticado")
    @GetMapping("/cart")
    @PreAuthorize("hasAuthority('SHOP_CART_OWN')")
    public ResponseEntity<ShopCartResponseDTO> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartConverter.toResponse(shopUseCase.getCart(authentication.getName())));
    }

    @Operation(summary = "Upsert de quantidade de uma linha do carrinho",
            description = "PUT idempotente: define a quantidade, não incrementa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado no catálogo", content = @Content),
            @ApiResponse(responseCode = "409", description = "Produto sem preço", content = @Content)
    })
    @PutMapping("/cart/items/{sku}")
    @PreAuthorize("hasAuthority('SHOP_CART_OWN')")
    public ResponseEntity<ShopCartResponseDTO> upsertCartItem(@PathVariable String sku,
            @Valid @RequestBody CartItemRequest request, Authentication authentication) {
        ShopUseCase.CartView cart = shopUseCase.upsertCartItem(authentication.getName(), sku, request.getQuantity());
        return ResponseEntity.ok(cartConverter.toResponse(cart));
    }

    @Operation(summary = "Remove uma linha do carrinho")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "404", description = "Item não estava no carrinho", content = @Content)
    })
    @DeleteMapping("/cart/items/{sku}")
    @PreAuthorize("hasAuthority('SHOP_CART_OWN')")
    public ResponseEntity<Void> removeCartItem(@PathVariable String sku, Authentication authentication) {
        shopUseCase.removeCartItem(authentication.getName(), sku);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Checkout: cria o pedido a partir do carrinho inteiro, reserva o estoque e cria a cobrança",
            description = "Preço, custo e taxa de cashback são resolvidos do catálogo agora, não do "
                    + "que estava no carrinho. O carrinho só é esvaziado se tudo suceder — pedido, "
                    + "reserva de estoque e cobrança no gateway (ECM-F004), na mesma transação. A "
                    + "resposta traz checkoutUrl: a URL hospedada pelo gateway para onde o cliente é "
                    + "redirecionado para pagar.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado, AGUARDANDO_PAGAMENTO"),
            @ApiResponse(responseCode = "400", description = "Estoque insuficiente", content = @Content),
            @ApiResponse(responseCode = "409", description = "Carrinho vazio, ou item sem preço", content = @Content),
            @ApiResponse(responseCode = "502", description = "Falha ao criar cobrança no gateway", content = @Content),
            @ApiResponse(responseCode = "503", description = "Depósito padrão do marketplace não configurado", content = @Content)
    })
    @PostMapping("/checkout")
    @PreAuthorize("hasAuthority('SHOP_ORDER_OWN')")
    public ResponseEntity<OrderResponseDTO> checkout(Authentication authentication) {
        ShopUseCase.CheckoutResult result = shopUseCase.checkout(authentication.getName());
        // Depois do commit (o use case já retornou): dispara as automações de PEDIDO_CRIADO.
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", result.order().id());
        details.put("orderNumber", String.valueOf(result.order().orderNumber()));
        if (result.order().customerId() != null) {
            details.put("customerId", result.order().customerId());
        }
        publisher.publishEvent(AuditEvent.of(EventType.ORDER_CREATED, authentication.getName(), details));
        return ResponseEntity.status(201).body(orderConverter.toCheckoutResponse(result.order(), result.checkoutUrl()));
    }

    @Operation(summary = "Pedidos do cliente autenticado, de qualquer canal, do mais recente para o mais antigo")
    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('SHOP_ORDER_OWN')")
    public ResponseEntity<PageResult<OrderResponseDTO>> listMyOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication) {
        PageResult<Order> result = shopUseCase.listMyOrders(authentication.getName(), page, size);
        PageResult<OrderResponseDTO> response = new PageResult<>(
                result.content().stream().map(orderConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Um pedido do cliente autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Não encontrado — inclusive para pedido de outro cliente", content = @Content)
    })
    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAuthority('SHOP_ORDER_OWN')")
    public ResponseEntity<OrderResponseDTO> getMyOrder(@PathVariable("id") Long orderId,
            Authentication authentication) {
        Order order = shopUseCase.getMyOrder(authentication.getName(), orderId);
        return ResponseEntity.ok(orderConverter.toResponse(order));
    }

    @Operation(summary = "Cancela um pedido do cliente ANTES de pagamento confirmado",
            description = "Libera a reserva de estoque. Pedido com pagamento já confirmado não pode "
                    + "ser cancelado por aqui — só o operador reembolsa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cancelado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado — inclusive para pedido de outro cliente", content = @Content),
            @ApiResponse(responseCode = "409", description = "Transição não permitida: pagamento já confirmado", content = @Content)
    })
    @PostMapping("/orders/{id}/cancel")
    @PreAuthorize("hasAuthority('SHOP_ORDER_OWN')")
    public ResponseEntity<OrderResponseDTO> cancelMyOrder(@PathVariable("id") Long orderId,
            @Valid @RequestBody OrderCancelRequest request, Authentication authentication) {
        Order before = shopUseCase.getMyOrder(authentication.getName(), orderId);
        Order order = shopUseCase.cancelMyOrder(authentication.getName(), orderId, request.getReason());
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", orderId);
        details.put("orderNumber", String.valueOf(order.orderNumber()));
        details.put("statusBefore", before.status().name());
        details.put("reason", request.getReason());
        publisher.publishEvent(AuditEvent.of(EventType.ORDER_CANCELLED, authentication.getName(), details));
        return ResponseEntity.ok(orderConverter.toResponse(order));
    }
}
