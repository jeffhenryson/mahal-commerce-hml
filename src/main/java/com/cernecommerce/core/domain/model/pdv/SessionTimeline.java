package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;

import java.time.Duration;
import java.time.Instant;

/**
 * PDV-F035 — a linha do tempo de uma sessão de narguilé ({@code SESSAO} ou {@code ROSH_EXTRA}),
 * com quanto durou cada fase. Calculada dos horários que a linha já grava (V132), sem coluna nova.
 *
 * <p>Fases: <b>espera</b> (lançada → início do preparo: o pagamento, ou a fila do 2º rosh),
 * <b>preparo</b> (início → entregue), <b>na mesa</b> (entregue → recolhida). Duração de fase que
 * ainda não terminou vem nula. A sessão desistida ({@code PREPARANDO → RECOLHIDO}) não tem entrega:
 * o preparo vai até o recolhimento e {@code naMesaMin} fica nulo. Sessão anterior à V132 (backfill:
 * início = lançamento, sem entrega) só tem o total; as fases ficam nulas.</p>
 *
 * @param pagaEm    quando o pedido que cobrou a linha foi concluído; nulo enquanto a receber
 * @param esperouPor {@code PAGAMENTO}, {@code FILA} ou nulo (paga no final, ou linha sem espera)
 */
public record SessionTimeline(Long itemId, ConsumptionMode mode, Long linkedItemId, String productName,
        boolean pagarNoFinal, SessionStatus status, boolean desistida, Espera esperouPor,
        Instant lancadaEm, Instant pagaEm, Instant inicioEm, Instant entregueEm, Instant recolhidaEm,
        Long esperaMin, Long preparoMin, Long naMesaMin, Long totalMin) {

    /** O que a sessão esperou antes de ir ao preparo. */
    public enum Espera { PAGAMENTO, FILA }

    /**
     * @param paidAt conclusão do pedido em {@code closedInOrderId}; nulo se a linha não foi cobrada
     * @return nulo se a linha não é de sessão ou não tem status (anterior à V132)
     */
    public static SessionTimeline of(ComandaItem item, Instant paidAt) {
        if (item == null || !item.mode().isMenuSession() || item.session() == null) {
            return null;
        }
        SessionProgress s = item.session();
        boolean semEntrega = s.status() == SessionStatus.RECOLHIDO && s.deliveredAt() == null && s.startedAt() != null;
        // O backfill da V132 deu às sessões de mesa já encerrada RECOLHIDO com início = lançamento e
        // sem entrega. Não é desistência, é falta de registro: as fases dela ficam desconhecidas.
        boolean backfill = semEntrega && !s.payLater() && s.startedAt().equals(item.addedAt());
        boolean desistida = semEntrega && !backfill;
        Instant fimPreparo = s.deliveredAt() != null ? s.deliveredAt() : (desistida ? s.collectedAt() : null);
        return new SessionTimeline(item.id(), item.mode(), item.linkedItemId(), item.productName(), s.payLater(),
                s.status(), desistida, esperaDe(item, s), item.addedAt(), paidAt, s.startedAt(), s.deliveredAt(),
                s.collectedAt(),
                backfill ? null : minutes(item.addedAt(), s.startedAt()),
                minutes(s.startedAt(), fimPreparo),
                minutes(s.deliveredAt(), s.collectedAt()),
                minutes(item.addedAt(), s.collectedAt()));
    }

    private static Espera esperaDe(ComandaItem item, SessionProgress s) {
        if (s.payLater() && item.mode() == ConsumptionMode.SESSAO) {
            return null;
        }
        // O 2º rosh espera o narguilé ficar livre; a sessão espera o pagamento (PDV-F027).
        return item.mode() == ConsumptionMode.ROSH_EXTRA ? Espera.FILA : Espera.PAGAMENTO;
    }

    /** Minutos entre os dois instantes; nulo se algum falta. */
    public static Long minutes(Instant from, Instant to) {
        if (from == null || to == null) {
            return null;
        }
        return Duration.between(from, to).toMinutes();
    }
}
