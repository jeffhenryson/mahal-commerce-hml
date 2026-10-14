package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** PDV-F045 — o saldo disponível dos dois níveis depois de abrir a embalagem. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AbrirEmbalagemResponseDTO {

    @Schema(description = "A embalagem aberta (a carteira, ou o maço).")
    private Nivel aberto;

    @Schema(description = "O nível de dentro, que recebeu o conteúdo (os maços, ou os soltos).")
    private Nivel dentro;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Nivel {
        @Schema(example = "LM-AZUL-MACO")
        private String sku;

        @Schema(description = "Disponível no depósito do caixa.", example = "12")
        private BigDecimal available;
    }
}
