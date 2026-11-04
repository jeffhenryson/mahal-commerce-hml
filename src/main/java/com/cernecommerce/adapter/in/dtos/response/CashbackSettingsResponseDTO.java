package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CashbackSettingsResponseDTO {
    private int carenciaDias;
    private int expiracaoDias;
    private BigDecimal resgateTetoPercent;
    private BigDecimal resgateMinimoSaldo;
}
