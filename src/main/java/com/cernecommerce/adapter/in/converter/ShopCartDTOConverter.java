package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.ShopCartItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCartResponseDTO;
import com.cernecommerce.core.ports.in.ShopUseCase.CartItemView;
import com.cernecommerce.core.ports.in.ShopUseCase.CartView;

public class ShopCartDTOConverter {

    public ShopCartResponseDTO toResponse(CartView cart) {
        ShopCartResponseDTO dto = new ShopCartResponseDTO();
        dto.setItems(cart.items().stream().map(this::toResponse).toList());
        dto.setTotal(cart.total());
        dto.setDiscountTotal(cart.discountTotal());
        dto.setPromotionDiscountTotal(cart.promotionDiscountTotal());
        dto.setUpdatedAt(cart.updatedAt());
        return dto;
    }

    private ShopCartItemResponseDTO toResponse(CartItemView item) {
        ShopCartItemResponseDTO dto = new ShopCartItemResponseDTO();
        dto.setSku(item.sku());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setSubtotal(item.subtotal());
        dto.setAvailable(item.available());
        dto.setKitBundleId(item.kitBundleId());
        dto.setKitTemplateId(item.kitTemplateId());
        dto.setKitStepId(item.kitStepId());
        dto.setDiscountAmount(item.discountAmount());
        dto.setPromotionId(item.promotionId());
        dto.setPromotionName(item.promotionName());
        dto.setPromotionDiscountAmount(item.promotionDiscountAmount());
        return dto;
    }
}
