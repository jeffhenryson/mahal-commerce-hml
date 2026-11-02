package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Corpo de criação/substituição de uma promoção por quantidade. {@code quantity} e {@code bundlePrice}
 * não têm {@code @Min}: o domínio recusa com o código próprio (PROMOTION_INVALID_QUANTITY /
 * PROMOTION_INVALID_PRICE), que o front traduz.
 */
@Data
@Schema(description = "Promoção por quantidade: N unidades de qualquer combinação dos SKUs por bundlePrice")
public class QuantityPromotionRequest {

    @NotBlank
    @Size(max = 150)
    @Schema(example = "Seda — leve 4 por R$ 10")
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull
    @Schema(description = "Unidades por grupo (N ≥ 2)", example = "4")
    private Integer quantity;

    @NotNull
    @Schema(description = "Preço do grupo de N unidades (> 0)", example = "10.00")
    private BigDecimal bundlePrice;

    @NotEmpty
    @Schema(description = "SKUs vendáveis elegíveis (mix & match)")
    private List<@NotBlank @Size(max = 50) String> skus;

    @NotEmpty
    private List<@NotNull PromotionChannel> channels;

    @Schema(description = "Nulo vale true")
    private Boolean active;

    @Schema(description = "Início (ISO-8601). Nulo: sem limite", nullable = true)
    private OffsetDateTime startsAt;

    @Schema(description = "Fim, exclusivo (ISO-8601). Nulo: sem limite", nullable = true)
    private OffsetDateTime endsAt;
}
