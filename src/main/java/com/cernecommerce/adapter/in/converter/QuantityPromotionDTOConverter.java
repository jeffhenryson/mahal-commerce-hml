package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.QuantityPromotionRequest;
import com.cernecommerce.adapter.in.dtos.response.QuantityPromotionBadgeDTO;
import com.cernecommerce.adapter.in.dtos.response.QuantityPromotionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.QuantityPromotionStatsResponseDTO;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.ports.in.QuantityPromotionUseCase.PromotionStats;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;

public class QuantityPromotionDTOConverter {

    public QuantityPromotion toDomain(QuantityPromotionRequest r) {
        return new QuantityPromotion(null, r.getName(), r.getDescription(), r.getQuantity(), r.getBundlePrice(),
                r.getSkus(), EnumSet.copyOf(r.getChannels()), r.getActive() == null || r.getActive(),
                toInstant(r.getStartsAt()), toInstant(r.getEndsAt()), null, null, null, null);
    }

    public QuantityPromotionResponseDTO toResponse(QuantityPromotion p, Instant now) {
        QuantityPromotionResponseDTO dto = new QuantityPromotionResponseDTO();
        dto.setId(p.id());
        dto.setName(p.name());
        dto.setDescription(p.description());
        dto.setQuantity(p.quantity());
        dto.setBundlePrice(p.bundlePrice());
        dto.setSkus(p.skus());
        dto.setChannels(p.channels().stream().sorted().toList());
        dto.setActive(p.active());
        dto.setStartsAt(p.startsAt());
        dto.setEndsAt(p.endsAt());
        dto.setStatus(p.status(now));
        dto.setLabel(p.label());
        dto.setCreatedAt(p.createdAt());
        dto.setUpdatedAt(p.updatedAt());
        return dto;
    }

    public List<QuantityPromotionResponseDTO> toResponse(List<QuantityPromotion> list, Instant now) {
        return list.stream().map(p -> toResponse(p, now)).toList();
    }

    public QuantityPromotionBadgeDTO toBadge(QuantityPromotion p) {
        return new QuantityPromotionBadgeDTO(p.id(), p.quantity(), p.bundlePrice(), p.label());
    }

    public QuantityPromotionStatsResponseDTO toResponse(PromotionStats s) {
        QuantityPromotionStatsResponseDTO dto = new QuantityPromotionStatsResponseDTO();
        dto.setVendas(s.vendas());
        dto.setGrupos(s.grupos());
        dto.setUnidades(s.unidades());
        dto.setDescontoTotal(s.descontoTotal());
        return dto;
    }

    private static Instant toInstant(OffsetDateTime t) {
        return t == null ? null : t.toInstant();
    }
}
