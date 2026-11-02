package com.cernecommerce.adapter.in.dtos.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Endereço de entrega (PDV-F022). Numa ENTREGA, street, number, city e state são obrigatórios. */
@Data
public class DeliveryAddressRequest {

    @Size(max = 200)
    @Schema(example = "Rua das Flores")
    private String street;

    @Size(max = 20)
    @Schema(example = "123")
    private String number;

    @Size(max = 100)
    @Schema(example = "Apto 12")
    private String complement;

    @Size(max = 20)
    @Schema(example = "58000-000")
    private String zipCode;

    @Size(max = 100)
    @Schema(example = "Centro")
    private String district;

    @Size(max = 100)
    @Schema(example = "João Pessoa")
    private String city;

    @Size(max = 50)
    @Schema(example = "PB")
    private String state;

    @Size(max = 60)
    @Schema(example = "Brasil")
    private String country;

    @Size(max = 200)
    @Schema(example = "Portão azul, ao lado da padaria")
    private String reference;

    @jakarta.validation.constraints.DecimalMin("-90")
    @jakarta.validation.constraints.DecimalMax("90")
    @io.swagger.v3.oas.annotations.media.Schema(description = "Pendência 10 §2 — latitude do endereço (vem junto com "
            + "longitude, ou nenhuma). O app manda quando usa o autocomplete do Google.", example = "-22.9035",
            nullable = true)
    private Double latitude;

    @jakarta.validation.constraints.DecimalMin("-180")
    @jakarta.validation.constraints.DecimalMax("180")
    @io.swagger.v3.oas.annotations.media.Schema(description = "Pendência 10 §2 — longitude do endereço.",
            example = "-43.1092", nullable = true)
    private Double longitude;
}
