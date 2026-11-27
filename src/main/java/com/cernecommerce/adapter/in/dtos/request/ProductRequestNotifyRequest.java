package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.ProductRequestNotifyChannel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Aviso aos clientes. Sem {@code entryIds}: todas as solicitações ativas ainda não avisadas. */
@Data
public class ProductRequestNotifyRequest {

    @Schema(nullable = true)
    private List<Long> entryIds;

    @NotNull
    @Schema(description = "AUTOMATION dispara PEDIDO_PRODUTO_CHEGOU; MANUAL só registra o aviso")
    private ProductRequestNotifyChannel channel;
}
