package com.cernecommerce.core.domain.model.salao;

import java.time.Instant;

/**
 * Mesa do Salão com QR de autoatendimento. Existe só para o QR: a comanda continua com o rótulo
 * livre ({@code tableOrCustomerLabel} = {@link #nome}), e nada do PDV depende deste cadastro.
 *
 * <p>O {@link #token} vai na URL do QR. É aleatório e regenerável — trocar invalida o QR impresso.</p>
 */
public record SalaoMesa(Long id, String nome, String token, boolean ativo, Instant criadoEm) {

    public static final int NOME_MAX = 60;

    public SalaoMesa {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        nome = nome.trim();
        if (nome.length() > NOME_MAX) {
            throw new IllegalArgumentException("nome passa de " + NOME_MAX + " caracteres");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token é obrigatório");
        }
    }

    public SalaoMesa withNome(String novoNome) {
        return new SalaoMesa(id, novoNome, token, ativo, criadoEm);
    }

    public SalaoMesa withAtivo(boolean novoAtivo) {
        return new SalaoMesa(id, nome, token, novoAtivo, criadoEm);
    }

    public SalaoMesa withToken(String novoToken) {
        return new SalaoMesa(id, nome, novoToken, ativo, criadoEm);
    }
}
