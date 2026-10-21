package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.ProporcaoMistura;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Pedido de sessão pelo QR. O cliente é resolvido de novo no servidor pelo CPF/celular.
 * {@code dispositivo} é o token do aparelho, gerado no navegador — é ele que fica com a mesa.
 * {@code proporcao} só vale com {@code essenciaSku2}; sem ele é ignorada.
 */
public record SalaoPedidoRequest(
        @Size(max = 20) String cpf,
        @Size(max = 20) String celular,
        @NotBlank @Size(max = 50) String essenciaSku,
        @Size(max = 50) String essenciaSku2,
        ProporcaoMistura proporcao,
        @Size(max = 200) String observacao,
        boolean vasoGrande,
        Charcoal carvao,
        @Size(max = 10) List<Long> adicionalIds,
        boolean pagarNoFinal,
        @NotBlank @Size(max = 64) String dispositivo) {
}
