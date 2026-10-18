package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** PLAT-F005 — o cliente do CRM a ligar à conta de operador. */
@Data
public class UserCustomerLinkRequest {

    @NotNull
    private Long customerId;
}
