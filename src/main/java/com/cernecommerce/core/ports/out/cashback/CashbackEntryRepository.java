package com.cernecommerce.core.ports.out.cashback;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionAllocation;
import com.cernecommerce.core.domain.model.cashback.RedeemableEarning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * Sobra (ganho menos o consumido por resgate) dos ganhos já disponíveis cujo {@code expiresAt}
     * cai entre {@code from} e {@code to} e que ainda não foram expirados pelo varredor.
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

    // ------------------------------------------------------------------------------------------
    // V186 — resgate
    // ------------------------------------------------------------------------------------------

    /**
     * Trava o cliente até o fim da transação — serializa resgate, devolução e expiração do mesmo
     * cliente, inclusive entre instâncias. Um lock em memória não basta: seria liberado antes do
     * commit, e outra transação leria o saldo antigo.
     */
    void lockCustomer(Long customerId);

    /**
     * Ganhos do cliente consumíveis agora (liberados, não vencidos, não anulados) com sobra
     * positiva, em ordem FIFO de vencimento.
     */
    List<RedeemableEarning> findRedeemableEarned(Long customerId, Instant now);

    /** Quanto de cada ganho já foi consumido por resgate (líquido de devoluções). */
    Map<Long, BigDecimal> sumConsumedByEarnedIds(Collection<Long> earnedIds);

    /** O {@code REDEEMED} do pedido, se houve resgate nele. */
    Optional<CashbackEntry> findRedeemedByOrderId(Long orderId);

    /** Total já devolvido ({@code REDEMPTION_REVERSED}) de um resgate. */
    BigDecimal sumRedemptionReversed(Long redeemedId);

    /** Alocação líquida de um resgate por ganho: consumido menos o já devolvido. */
    Map<Long, BigDecimal> sumNetAllocationByRedemption(Long redeemedId);

    List<CashbackEntry> findAllById(Collection<Long> ids);

    void saveAllocation(CashbackRedemptionAllocation allocation);
}

