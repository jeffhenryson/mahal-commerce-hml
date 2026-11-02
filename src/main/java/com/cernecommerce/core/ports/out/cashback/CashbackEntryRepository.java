package com.cernecommerce.core.ports.out.cashback;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Port de saída para o ledger de cashback (CRM-F003) — append-only: não há {@code update} nem
 * {@code delete}, só {@link #save} e leituras agregadas.
 */
public interface CashbackEntryRepository {

    CashbackEntry save(CashbackEntry entry);

    java.util.Optional<CashbackEntry> findById(Long id);

    /** Já existe estorno ou expiração apontando para este lançamento? */
    boolean isReversed(Long entryId);

    /** Libera agora os ganhos em carência do cliente. Devolve quantos lançamentos mudaram. */
    int releasePendingEarned(Long customerId, Instant now);

    PageResult<CashbackEntry> findByCustomerId(Long customerId, int page, int size);

    /** {@code SUM(amount)} das entradas do cliente já liberadas em {@code now} — o saldo disponível. */
    BigDecimal sumAvailableByCustomerId(Long customerId, Instant now);

    /** {@code SUM(amount)} dos ganhos {@code EARNED} ainda em carência (não liberados) em {@code now}. */
    BigDecimal sumPendingByCustomerId(Long customerId, Instant now);

    /**
     * {@code SUM(amount)} dos ganhos já disponíveis cujo {@code expiresAt} cai entre {@code from} e
     * {@code to} e que ainda não foram expirados pelo varredor.
     */
    BigDecimal sumExpiringSoonByCustomerId(Long customerId, Instant from, Instant to);

    /**
     * Entradas {@code EARNED} cujo {@code expiresAt} já passou em {@code now} e que ainda não têm
     * uma entrada {@code EXPIRED} referenciando-as, no máximo {@code limit} — mesmo padrão de lote
     * do varredor de reserva de estoque, para uma janela de indisponibilidade longa não virar o
     * próprio incidente.
     */
    List<CashbackEntry> findEarnedPendingExpiry(Instant now, int limit);

    /**
     * Entradas {@code EARNED} do pedido ainda não revertidas — nem por {@code REVERSED} (um
     * reembolso anterior) nem por {@code EXPIRED} (o varredor, que pode ter alcançado a entrada
     * primeiro se o pedido ficou tempo suficiente sem ser reembolsado). Mesma guarda de
     * {@link #findEarnedPendingExpiry}: sem ela, reembolsar um pedido cujo cashback já expirou
     * duplicaria o débito.
     */
    List<CashbackEntry> findEarnedByOrderId(Long orderId);

    /**
     * Existe algum lançamento {@code EARNED} para este pedido — revertido ou não. Distinto de
     * {@link #findEarnedByOrderId}, que propositalmente esconde entradas já revertidas: esta
     * checagem serve só para a guarda de idempotência de {@code recordEarnedForOrder} (ECM-F004/
     * achado de {@code CashbackLedgerConcurrencyIT}) — "este pedido já teve cashback lançado
     * alguma vez" não pode dar falso-negativo só porque o lançamento original já foi revertido.
     */
    boolean existsEarnedForOrder(Long orderId);
}
