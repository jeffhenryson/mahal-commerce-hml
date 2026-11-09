package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** PDV-F061 — em quais modalidades novas da sessão a essência vale. Campo omitido mantém o atual. */
@Data
public class SessionEssenceFlagsRequest {

    @Schema(description = "Vale na sessão com compra do pacote.", example = "true")
    private Boolean compraEssencia;

    @Schema(description = "Vale na sessão com itens pessoais do cliente.", example = "false")
    private Boolean itensPessoais;
}
