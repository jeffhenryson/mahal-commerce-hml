package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.PromotionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Schema(description = "Promoção por quantidade")
public class QuantityPromotionResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Integer quantity;
    private BigDecimal bundlePrice;
    private List<String> skus;
    private List<PromotionChannel> channels;
    private boolean active;
    private Instant startsAt;
    private Instant endsAt;
    @Schema(description = "Situação agora: ATIVA, AGENDADA, ENCERRADA ou INATIVA")
    private PromotionStatus status;
    @Schema(description = "Selo curto: \"4 por R$ 10\"")
    private String label;
    private Instant createdAt;
    private Instant updatedAt;
}
