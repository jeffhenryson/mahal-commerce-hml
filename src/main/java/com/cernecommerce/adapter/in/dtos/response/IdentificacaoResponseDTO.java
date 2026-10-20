package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.ports.in.SalaoAutoatendimentoUseCase.Identificado;

import java.util.List;

/**
 * Resposta do "Já sou cliente" e do cadastro: se achou, o primeiro nome mascarado ("Ma***"), se é VIP
 * (só VIP pode pagar no final) e o que falta no cadastro ({@code celular}, {@code cpf},
 * {@code dataNascimento}).
 */
public record IdentificacaoResponseDTO(boolean encontrado, String nome, boolean vip, List<String> faltando) {

    public static IdentificacaoResponseDTO of(Identificado i) {
        return new IdentificacaoResponseDTO(true, i.nome(), i.vip(), i.faltando());
    }

    public static IdentificacaoResponseDTO naoEncontrado() {
        return new IdentificacaoResponseDTO(false, null, false, List.of());
    }
}
