package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alteração parcial de depósito (EST-F018). Campo ausente ou nulo significa <b>não mexer</b>.
 * {@code code} não entra: é a identidade pública usada em toda a API como {@code warehouseCode}.
 */
@Data
public class WarehousePatchRequest {

    @Size(min = 1, max = 255)
    private String name;

    /** {@code LOJA_FISICA}, {@code ECOMMERCE} ou {@code SALAO}. Valor desconhecido é 400. */
    private String type;
}
