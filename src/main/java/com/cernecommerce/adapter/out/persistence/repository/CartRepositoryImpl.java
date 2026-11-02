package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CartEntity;
import com.cernecommerce.adapter.out.persistence.entity.CartItemEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.domain.model.ecommerce.CartItem;
import com.cernecommerce.core.ports.out.ecommerce.CartRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@Transactional
public class CartRepositoryImpl implements CartRepository {

    private final CartJpaRepository cartJpaRepository;

    public CartRepositoryImpl(CartJpaRepository cartJpaRepository) {
        this.cartJpaRepository = cartJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cart> findByCustomerId(Long customerId) {
        return cartJpaRepository.findByCustomerId(customerId).map(this::toDomain);
    }

    @Override
    public Cart upsertItem(Long customerId, String sku, BigDecimal quantity) {
        CartEntity cart = findOrCreate(customerId);

        // Só linha avulsa: a mesma seda dentro de um kit (ECM-F008) é outra linha, e mexer nela
        // aqui mudaria a quantidade de um item do pacote por fora da cotação.
        Optional<CartItemEntity> existing = cart.getItems().stream()
                .filter(item -> item.getKitBundleId() == null && item.getSku().equals(sku))
                .findFirst();
        if (existing.isPresent()) {
            existing.get().setQuantity(quantity);
        } else {
            CartItemEntity item = new CartItemEntity();
            item.setCart(cart);
            item.setSku(sku);
            item.setQuantity(quantity);
            cart.getItems().add(item);
        }
        cart.setUpdatedAt(Instant.now());
        return toDomain(cartJpaRepository.save(cart));
    }

    @Override
    public Cart addKitBundle(Long customerId, List<CartItem> items) {
        CartEntity cart = findOrCreate(customerId);
        for (CartItem item : items) {
            CartItemEntity entity = new CartItemEntity();
            entity.setCart(cart);
            entity.setSku(item.sku());
            entity.setQuantity(item.quantity());
            entity.setKitBundleId(item.kitBundleId());
            entity.setKitTemplateId(item.kitTemplateId());
            entity.setKitStepId(item.kitStepId());
            cart.getItems().add(entity);
        }
        cart.setUpdatedAt(Instant.now());
        return toDomain(cartJpaRepository.save(cart));
    }

    @Override
    public boolean removeKitBundle(Long customerId, String kitBundleId) {
        Optional<CartEntity> cartOpt = cartJpaRepository.findByCustomerId(customerId);
        if (cartOpt.isEmpty()) {
            return false;
        }
        CartEntity cart = cartOpt.get();
        boolean removed = cart.getItems().removeIf(item -> kitBundleId.equals(item.getKitBundleId()));
        if (removed) {
            cart.setUpdatedAt(Instant.now());
            cartJpaRepository.save(cart);
        }
        return removed;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Cart> findAllNonEmpty(int page, int size) {
        Page<Long> ids = cartJpaRepository.findNonEmptyIds(PageRequest.of(page, size));
        Map<Long, CartEntity> byId = new HashMap<>();
        if (!ids.isEmpty()) {
            cartJpaRepository.findWithItemsByIdIn(ids.getContent()).forEach(c -> byId.put(c.getId(), c));
        }
        List<Cart> content = ids.getContent().stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(this::toDomain)
                .toList();
        return new PageResult<>(content, page, size, ids.getTotalElements(), ids.getTotalPages());
    }

    private CartEntity findOrCreate(Long customerId) {
        return cartJpaRepository.findByCustomerId(customerId).orElseGet(() -> {
            CartEntity created = new CartEntity();
            created.setCustomerId(customerId);
            created.setCreatedAt(Instant.now());
            created.setUpdatedAt(Instant.now());
            return created;
        });
    }

    @Override
    public boolean removeItem(Long customerId, String sku) {
        Optional<CartEntity> cartOpt = cartJpaRepository.findByCustomerId(customerId);
        if (cartOpt.isEmpty()) {
            return false;
        }
        CartEntity cart = cartOpt.get();
        boolean removed = cart.getItems().removeIf(item -> item.getKitBundleId() == null && item.getSku().equals(sku));
        if (removed) {
            cart.setUpdatedAt(Instant.now());
            cartJpaRepository.save(cart);
        }
        return removed;
    }

    @Override
    public void clear(Long customerId) {
        cartJpaRepository.findByCustomerId(customerId).ifPresent(cart -> {
            if (!cart.getItems().isEmpty()) {
                cart.getItems().clear();
                cart.setUpdatedAt(Instant.now());
                cartJpaRepository.save(cart);
            }
        });
    }

    private Cart toDomain(CartEntity e) {
        var items = e.getItems().stream()
                .map(i -> new CartItem(i.getSku(), i.getQuantity(), i.getKitBundleId(), i.getKitTemplateId(),
                        i.getKitStepId()))
                .toList();
        return Cart.of(e.getId(), e.getCustomerId(), items, e.getUpdatedAt());
    }
}
