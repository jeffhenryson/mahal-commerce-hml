package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.converter.PedidoAppDTOConverter;
import com.cernecommerce.adapter.in.converter.ShopCartDTOConverter;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import com.cernecommerce.core.ports.in.ShopUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class ShopAccountControllerTest {

    private MockMvc mockMvc;
    private ShopUseCase shopUseCase;
    
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("user", null, List.of());

    @BeforeEach
    void setup() {
        shopUseCase = mock(ShopUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShopAccountController(shopUseCase, new ShopCartDTOConverter(),
                        new OrderDTOConverter(), publisher, mock(PedidoAppUseCase.class),
                        new PedidoAppDTOConverter(new OrderDTOConverter())))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCart_returns_200() throws Exception {
        when(shopUseCase.getCart("user")).thenReturn(new ShopUseCase.CartView(List.of(), BigDecimal.ZERO, Instant.now()));

        mockMvc.perform(get("/shop/cart").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void upsertCartItem_returns_200() throws Exception {
        when(shopUseCase.upsertCartItem("user", "SKU1", new BigDecimal("2")))
                .thenReturn(new ShopUseCase.CartView(List.of(), BigDecimal.ZERO, Instant.now()));

        mockMvc.perform(put("/shop/cart/items/SKU1")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    void removeCartItem_returns_204() throws Exception {
        mockMvc.perform(delete("/shop/cart/items/SKU1").principal(AUTH))
                .andExpect(status().isNoContent());

        verify(shopUseCase).removeCartItem("user", "SKU1");
    }

    @Test
    void listMyOrders_returns_200() throws Exception {
        when(shopUseCase.listMyOrders(eq("user"), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/shop/orders").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
