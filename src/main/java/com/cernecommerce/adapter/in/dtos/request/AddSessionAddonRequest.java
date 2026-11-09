package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** PDV-F061 — adicional incluído na sessão já lançada (filtro de gelo, piteira). */
@Data
public class AddSessionAddonRequest {

    @NotNull
    @Schema(description = "Adicional ativo do cardápio (GET /pdv/sessao/cardapio → adicionais). Com a sessão "
            + "ENTREGUE, só os marcados permitePosEntrega.", example = "7")
    private Long adicionalId;
}
