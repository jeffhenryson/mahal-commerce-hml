package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.ecommerce.MotivoRecusa;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Recusa de um pedido do app pela Central de Pedidos (ECM-F012). */
@Data
public class OrderRejectRequest {

    @NotNull
    @Schema(description = "Motivo padronizado, lido pelo indicador de recusas.", example = "SEM_ESTOQUE")
    private MotivoRecusa reasonCode;

    @Size(max = MotivoRecusa.NOTA_MAX)
    @Schema(description = "Texto livre que vai ao cliente junto do motivo. Obrigatório com OUTRO.",
            example = "Essência acabou")
    private String note;
}
