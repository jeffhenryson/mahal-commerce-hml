package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/** O que o app precisa saber antes do checkout (ECM-F013) — {@code GET /shop/store-status}, público. */
@Schema(name = "LojaStatus")
public record LojaStatusResponseDTO(
        @Schema(description = "Recebendo e dentro do horário agora. Falso: o checkout responde 409 LOJA_FECHADA.")
        boolean aberto,
        boolean appRecebendo,
        boolean entregaHabilitada,
        boolean retiradaHabilitada,
        @Schema(description = "Estimativa, em minutos.") int tempoPreparoMin,
        @Schema(description = "Estimativa, em minutos.") int tempoEntregaMin,
        @Schema(description = "Mínimo para entrega; zero = sem mínimo.") BigDecimal pedidoMinimo,
        @Schema(description = "Taxa de referência; a definitiva é informada pela loja no aceite.") BigDecimal taxaFixa,
        @Schema(description = "Vazio = entrega em qualquer bairro.") List<Bairro> bairros) {

    @Schema(name = "LojaStatusBairro")
    public record Bairro(String nome, BigDecimal taxa) {
    }
}
