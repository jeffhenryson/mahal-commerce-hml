package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Corpo de {@code PUT /estoque/products/{sku}/kit-items}: substitui a lista inteira. */
@Data
public class KitItemsRequest {
    @NotNull
    @Valid
    @Size(max = 100)
    private List<KitItemRequest> items;
}
