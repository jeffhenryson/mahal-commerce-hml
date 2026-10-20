package com.cernecommerce.core.domain.model.salao;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Pedido de sessão feito pelo lead no QR da mesa, à espera do atendente.
 *
 * <p>{@link #codigo} é o id público (a página do lead acompanha o status por ele); o {@link #id}
 * sequencial só circula no admin. {@link #tierId} é a faixa resolvida no pedido, para o PDV mostrar
 * o preço — o lançamento, ao aceitar, resolve a faixa de novo pela essência.</p>
 *
 * <p>{@link #deviceToken} é o aparelho que pediu: no aceite que ocupa a mesa, é ele que fica com
 * acesso a ela.</p>
 */
public record SolicitacaoSessao(Long id, UUID codigo, Long mesaId, Long customerId, Long tierId,
        String essenciaSku, String essenciaSku2, String observacao, SolicitacaoStatus status, String motivo,
        Long comandaId, Instant criadoEm, Instant decididoEm, String decididoPor, EscolhasSessao escolhas,
        String deviceToken) {

    /** Depois disso a pendente vira {@link SolicitacaoStatus#EXPIRADA} na leitura. */
    public static final Duration VALIDADE = Duration.ofMinutes(30);
    public static final int TEXTO_MAX = 200;

    public SolicitacaoSessao {
        escolhas = escolhas == null ? EscolhasSessao.padrao() : escolhas;
    }

    public static SolicitacaoSessao nova(Long mesaId, Long customerId, Long tierId, String essenciaSku,
            String essenciaSku2, String observacao, EscolhasSessao escolhas, String deviceToken, Instant agora) {
        return new SolicitacaoSessao(null, UUID.randomUUID(), mesaId, customerId, tierId, essenciaSku,
                essenciaSku2, limpar(observacao), SolicitacaoStatus.PENDENTE, null, null, agora, null, null, escolhas,
                deviceToken);
    }

    /** O status como o mundo o vê: a pendente vencida é {@link SolicitacaoStatus#EXPIRADA}. */
    public SolicitacaoStatus statusEm(Instant agora) {
        if (status == SolicitacaoStatus.PENDENTE && criadoEm != null
                && criadoEm.plus(VALIDADE).isBefore(agora)) {
            return SolicitacaoStatus.EXPIRADA;
        }
        return status;
    }

    public SolicitacaoSessao aceita(Long novaComandaId, String username, Instant agora) {
        return new SolicitacaoSessao(id, codigo, mesaId, customerId, tierId, essenciaSku, essenciaSku2,
                observacao, SolicitacaoStatus.ACEITA, null, novaComandaId, criadoEm, agora, username, escolhas, deviceToken);
    }

    public SolicitacaoSessao recusada(String novoMotivo, String username, Instant agora) {
        return new SolicitacaoSessao(id, codigo, mesaId, customerId, tierId, essenciaSku, essenciaSku2,
                observacao, SolicitacaoStatus.RECUSADA, limpar(novoMotivo), null, criadoEm, agora, username, escolhas,
                deviceToken);
    }

    private static String limpar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        return t.length() > TEXTO_MAX ? t.substring(0, TEXTO_MAX) : t;
    }
}
