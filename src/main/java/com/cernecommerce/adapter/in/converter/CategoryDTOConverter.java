package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CategoryResponseDTO;
import com.cernecommerce.core.domain.model.estoque.Category;

import java.math.BigDecimal;

public class CategoryDTOConverter {

    public CategoryResponseDTO toResponse(Category category) {
        return toResponse(category, 0L, null);
    }

    public CategoryResponseDTO toResponse(Category category, long productCount) {
        return toResponse(category, productCount, null);
    }

    public CategoryResponseDTO toResponse(Category category, long productCount, BigDecimal averageMarginPercent) {
        CategoryResponseDTO dto = new CategoryResponseDTO();
        dto.setId(category.id());
        dto.setName(category.name());
        dto.setFeatured(category.featured());
        dto.setDisplayOrder(category.displayOrder());
        dto.setActive(category.active());
        dto.setCigarette(category.cigarette());
        dto.setProductCount(productCount);
        dto.setAverageMarginPercent(averageMarginPercent);
        return dto;
    }
}
