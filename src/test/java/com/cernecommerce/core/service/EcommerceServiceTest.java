package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.domain.model.ecommerce.CartItem;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EcommerceServiceTest {

    CartRepository cartRepository;
    EcommerceService ecommerceService;

    @BeforeEach
    void setUp() {
        cartRepository = mock(CartRepository.class);
        ecommerceService = new EcommerceService(cartRepository);
    }

    @Test
    void listCarts_delegatesToRepositoryWithPageAndSize() {
        Cart cart = Cart.of(7L, 42L, List.of(new CartItem("SEDA-01", BigDecimal.ONE, null, null, null)),
                Instant.parse("2026-11-01T12:00:00Z"));
        PageResult<Cart> page = new PageResult<>(List.of(cart), 2, 5, 11, 3);
        when(cartRepository.findAllNonEmpty(2, 5)).thenReturn(page);

        PageResult<Cart> result = ecommerceService.listCarts(2, 5);

        assertThat(result).isEqualTo(page);
        verify(cartRepository).findAllNonEmpty(2, 5);
    }
}
