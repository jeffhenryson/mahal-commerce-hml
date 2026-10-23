package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/** Entrega ou retirada do pedido (PDV-F022); {@code null} no pedido sem entrega. */
@Data
public class DeliveryResponseDTO {

    @Schema(description = "RETIRADA ou ENTREGA")
    private String type;

    @Schema(description = "Endereço da ENTREGA; null na RETIRADA")
    private Address address;

    @Schema(description = "MOTOBOY_LOJA, APP_99, UBER_FLASH ou CORREIOS")
    private String method;

    private String courierName;
    private String courierPhone;
    private String pickupCode;
    private String dropoffCode;
    private String trackingCode;

    @Schema(description = "Taxa de entrega cobrada do cliente — incluída em totalPayable, fora de netAmount")
    private BigDecimal fee;

    @Data
    public static class Address {
        private String street;
        private String number;
        private String complement;
        private String zipCode;
        private String district;
        private String city;
        private String state;
        private String country;
        private String reference;
    }
}
