package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class NfeImportLineResponseDTO {

    private Long id;
    private int itemNumber;
    private String supplierProductCode;
    private String ean;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private String lotCode;
    private LocalDate expiryDate;
    private String matchStatus;
    private String matchedSku;

    @io.swagger.v3.oas.annotations.media.Schema(description = "V182 — unidade comercial da nota (uCom), a de "
            + "`quantity` e `unitPrice`. Ex.: CX, UN, PCT.", nullable = true)
    private String unit;

    @io.swagger.v3.oas.annotations.media.Schema(description = "V182 — unidade tributável (uTrib).", nullable = true)
    private String taxUnit;

    @io.swagger.v3.oas.annotations.media.Schema(description = "V182 — quantidade tributável (qTrib). Quando uTrib é a "
            + "unidade de estoque, qTrib/quantity sugere o fator.", nullable = true)
    private BigDecimal taxQuantity;

    @io.swagger.v3.oas.annotations.media.Schema(description = "V182 — fator de embalagem. No preview, o lembrado "
            + "para este fornecedor + cProd (nulo se nunca informado, vale 1). Depois do confirm, o usado.",
            nullable = true)
    private Integer unitsPerPackage;
}
