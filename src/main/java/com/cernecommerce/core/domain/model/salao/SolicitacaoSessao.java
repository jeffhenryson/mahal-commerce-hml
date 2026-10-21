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
 *
 * <p>{@link #acompanhante}: pedido sem CPF de um aparelho que entrou na mesa pelo código. O cliente é
 * o titular da ocupação (cobra na comanda dele); o PDV mostra "Acompanhante de &lt;titular&gt;".</p>
 */
public record SolicitacaoSessao(Long id, UUID codigo, Long mesaId, Long customerId, Long tierId,
        String essenciaSku, String essenciaSku2, String observacao, SolicitacaoStatus status, String motivo,
        Long comandaId, Instant criadoEm, Instant decididoEm, String decididoPor, EscolhasSessao escolhas,
        String deviceToken, boolean acompanhante) {

    /** Depois disso a pendente vira {@link SolicitacaoStatus#EXPIRADA} na leitura. */
    public static final Duration VALIDADE = Duration.ofMinutes(30);
    public static final int TEXTO_MAX = 200;

    public SolicitacaoSessao {
        escolhas = escolhas == null ? EscolhasSessao.padrao() : escolhas;
    }

    public SolicitacaoSessao(Long id, UUID codigo, Long mesaId, Long customerId, Long tierId, String essenciaSku,
            String essenciaSku2, String observacao, SolicitacaoStatus status, String motivo, Long comandaId,
            Instant criadoEm, Instant decididoEm, String decididoPor, EscolhasSessao escolhas, String deviceToken) {
        this(id, codigo, mesaId, customerId, tierId, essenciaSku, essenciaSku2, observacao, status, motivo, comandaId,
                criadoEm, decididoEm, decididoPor, escolhas, deviceToken, false);
    }

    public static SolicitacaoSessao nova(Long mesaId, Long customerId, Long tierId, String essenciaSku,
            String essenciaSku2, String observacao, EscolhasSessao escolhas, String deviceToken, Instant agora) {
        return nova(mesaId, customerId, tierId, essenciaSku, essenciaSku2, observacao, escolhas, deviceToken, false,
                agora);
    }

    public static SolicitacaoSessao nova(Long mesaId, Long customerId, Long tierId, String essenciaSku,
            String essenciaSku2, String observacao, EscolhasSessao escolhas, String deviceToken, boolean acompanhante,
            Instant agora) {
        return new SolicitacaoSessao(null, UUID.randomUUID(), mesaId, customerId, tierId, essenciaSku,
                essenciaSku2, limpar(observacao), SolicitacaoStatus.PENDENTE, null, null, agora, null, null, escolhas,
                deviceToken, acompanhante);
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
                observacao, SolicitacaoStatus.ACEITA, null, novaComandaId, criadoEm, agora, username, escolhas, deviceToken,
                acompanhante);
    }

    public SolicitacaoSessao recusada(String novoMotivo, String username, Instant agora) {
        return new SolicitacaoSessao(id, codigo, mesaId, customerId, tierId, essenciaSku, essenciaSku2,
                observacao, SolicitacaoStatus.RECUSADA, limpar(novoMotivo), null, criadoEm, agora, username, escolhas,
                deviceToken, acompanhante);
    }

    private static String limpar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        String t = texto.trim();
        return t.length() > TEXTO_MAX ? t.substring(0, TEXTO_MAX) : t;
    }
}
