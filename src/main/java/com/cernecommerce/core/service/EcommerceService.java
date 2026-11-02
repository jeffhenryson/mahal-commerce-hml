package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.ports.in.EcommerceUseCase;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;

/**
 * Leitura administrativa dos carrinhos do marketplace.
 *
 * <p>O carrinho em si é operado pelo {@code ShopService} ({@code /shop/cart}); aqui só se lista o
 * que existe, para o admin ver carrinhos em aberto. Classe pura conectada via {@code @Bean} em
 * {@code CoreBeanConfig}.</p>
 */
public class EcommerceService implements EcommerceUseCase {

    private final CartRepository cartRepository;

    public EcommerceService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Override
    public PageResult<Cart> listCarts(int page, int size) {
        return cartRepository.findAllNonEmpty(page, size);
    }
}
