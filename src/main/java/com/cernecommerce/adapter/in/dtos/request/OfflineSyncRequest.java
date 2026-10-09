package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** O lote da fila offline (PDV-F043). Até 50 vendas por chamada; filas maiores vão em lotes. */
@Data
public class OfflineSyncRequest {

    @NotEmpty
    @Size(max = 50)
    @Valid
    private List<OfflineSaleRequest> sales;
}
