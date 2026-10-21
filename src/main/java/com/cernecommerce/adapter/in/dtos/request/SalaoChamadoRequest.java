package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.salao.FormaPagamentoChamado;
import com.cernecommerce.core.domain.model.salao.TipoChamado;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Chamado pelo QR. Sem {@code comandaRef}, na mesa deste QR, pelo aparelho com acesso. Com ela, na
 * comanda do cliente (a do PDV), e aí vão o CPF ou celular e a {@code confirmacao}.
 *
 * @param formaPagamento obrigatória em CONTA
 */
public record SalaoChamadoRequest(
        @NotNull TipoChamado tipo,
        FormaPagamentoChamado formaPagamento,
        @NotBlank @Size(max = 64) String dispositivo,
        @Size(max = 64) String comandaRef,
        @Size(max = 20) String cpf,
        @Size(max = 20) String celular,
        @Size(max = 8) String confirmacao) {
}
