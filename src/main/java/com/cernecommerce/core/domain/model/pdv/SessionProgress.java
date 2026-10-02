package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * Status e horários de uma linha de sessão (PDV-F023). Espelha as quatro colunas da V132 em
 * {@code comanda_item}; agrupadas aqui porque só existem juntas, e só em linha de sessão.
 *
 * @param startedAt quando o tempo de mesa começou — nulo enquanto {@link SessionStatus#NA_FILA} ou
 *                  {@link SessionStatus#AGUARDANDO_PAGAMENTO}
 * @param payLater  PDV-F034 — a sessão foi ao salão antes de paga e fica em aberto até a conta. Nunca
 *                  passa por {@link SessionStatus#AGUARDANDO_PAGAMENTO}.
 */
public record SessionProgress(SessionStatus status, Instant startedAt, Instant deliveredAt, Instant collectedAt,
        boolean payLater) {

    public SessionProgress {
        if (status == null) {
            throw new IllegalArgumentException("status da sessão é obrigatório");
        }
        if (status != SessionStatus.NA_FILA && status != SessionStatus.AGUARDANDO_PAGAMENTO && startedAt == null) {
            throw new IllegalArgumentException("sessão " + status + " exige startedAt");
        }
        if (payLater && status == SessionStatus.AGUARDANDO_PAGAMENTO) {
            throw new IllegalArgumentException("sessão paga no final não aguarda pagamento");
        }
    }

    /** Sessão paga antes de ir ao salão — o caso de sempre. */
    public SessionProgress(SessionStatus status, Instant startedAt, Instant deliveredAt, Instant collectedAt) {
        this(status, startedAt, deliveredAt, collectedAt, false);
    }

    /** Sessão lançada agora e já no preparo — o tempo de mesa começa aqui. */
    public static SessionProgress preparing(Instant at) {
        return new SessionProgress(SessionStatus.PREPARANDO, at, null, null);
    }

    /** PDV-F027 — sessão lançada, utensílio reservado, esperando o pagamento para ir ao preparo. */
    public static SessionProgress awaitingPayment() {
        return new SessionProgress(SessionStatus.AGUARDANDO_PAGAMENTO, null, null, null);
    }

    /**
     * PDV-F027 — o pagamento leva a sessão ao preparo; é aqui que o tempo de mesa começa. Fora do
     * {@link #advanceTo}, que é o caminho do operador, e por isso não aceita esta transição.
     */
    public SessionProgress paid(Instant at) {
        if (status != SessionStatus.AGUARDANDO_PAGAMENTO) {
            throw new IllegalStateException("sessão " + status + " não está aguardando pagamento");
        }
        return new SessionProgress(SessionStatus.PREPARANDO, at, null, null);
    }

    /**
     * PDV-F034 — sessão paga no final: vai direto ao preparo, sem pagamento. O tempo de mesa começa
     * aqui, e a linha fica em aberto até a conta.
     */
    public static SessionProgress preparingPayLater(Instant at) {
        return new SessionProgress(SessionStatus.PREPARANDO, at, null, null, true);
    }

    /** 2º rosh do duplo: espera o 1º ser recolhido para começar. */
    public static SessionProgress queued() {
        return queued(false);
    }

    /** PDV-F034 — idem, herdando de uma sessão paga no final a liberação para o preparo sem pagamento. */
    public static SessionProgress queued(boolean payLater) {
        return new SessionProgress(SessionStatus.NA_FILA, null, null, null, payLater);
    }

    /**
     * Avança para {@code next}, carimbando o horário correspondente.
     *
     * @throws IllegalStateException se a transição não é permitida — o service recusa antes com 409
     */
    public SessionProgress advanceTo(SessionStatus next, Instant at) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("transição de sessão inválida: " + status + " → " + next);
        }
        return switch (next) {
            case PREPARANDO -> new SessionProgress(next, at, deliveredAt, collectedAt, payLater);
            case ENTREGUE -> new SessionProgress(next, startedAt, at, collectedAt, payLater);
            case RECOLHIDO -> new SessionProgress(next, startedAt, deliveredAt, at, payLater);
            case NA_FILA, AGUARDANDO_PAGAMENTO -> throw new IllegalStateException("nada volta para " + next);
        };
    }

    public boolean isAwaitingPayment() {
        return status == SessionStatus.AGUARDANDO_PAGAMENTO;
    }

    public boolean isCollected() {
        return status == SessionStatus.RECOLHIDO;
    }
}
