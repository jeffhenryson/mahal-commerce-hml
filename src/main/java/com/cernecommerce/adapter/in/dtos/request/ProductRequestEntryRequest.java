package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.ProductRequestUrgency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma solicitação: cliente do CRM ({@code customerId}) ou avulso ({@code customerName} + {@code customerPhone}). */
@Data
public class ProductRequestEntryRequest {

    @Schema(description = "Cliente do CRM. Sem ele, nome e telefone são obrigatórios (CUSTOMER_REQUIRED)",
            nullable = true)
    private Long customerId;

    @Size(max = 120)
    @Schema(nullable = true)
    private String customerName;

    @Size(max = 20)
    @Schema(description = "10 a 13 dígitos; a máscara é removida pelo servidor", example = "(85) 99999-1234",
            nullable = true)
    private String customerPhone;

    @NotNull
    @Min(1)
    private Integer quantity;

    @DecimalMin("0.00")
    @Digits(integer = 10, fraction = 2)
    @Schema(description = "Quanto o cliente aceita pagar por unidade", nullable = true)
    private BigDecimal maxPrice;

    @Schema(description = "Padrão MEDIUM", nullable = true)
    private ProductRequestUrgency urgency;

    @Schema(description = "YYYY-MM-DD, hoje ou depois", nullable = true)
    private LocalDate neededBy;

    @Size(max = 500)
    @Schema(nullable = true)
    private String note;
}
