package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionQuote;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;
import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Caso de uso do programa de cashback (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <p>Cobre <b>ganhar</b> cashback (taxa por abrangência, ledger, lançamento na conclusão da venda,
 * expiração), as consultas de saldo/extrato/diagnóstico de margem e, desde a V186, o
 * <b>resgate</b> (balcão, comanda e app) com os parâmetros que o admin ajusta
 * ({@link #getSettings}/{@link #updateSettings}). A reversão por reembolso
 * ({@link #reverseEarningsForOrder}) é acionada pelo {@code OrderService.refundOrder}.</p>
 */
public interface CashbackUseCase {

    /**
     * Pendência 8 — revoga um ganho ({@code EARNED}) do cliente: grava um {@code REVERSED} apontando
     * para ele, como no reembolso. Nada é apagado.
     *
     * @throws com.cernecommerce.core.domain.exception.cashback.CashbackEntryNotFoundException se o
     *         lançamento não existir ou for de outro cliente
     * @throws com.cernecommerce.core.domain.exception.cashback.CashbackEntryNotRevocableException se
     *         não for um ganho ou já tiver sido revertido/expirado
     */
    com.cernecommerce.core.domain.model.cashback.CashbackEntry revokeEntry(Long customerId, Long entryId);

    /**
     * Pendência 8 — "desbloquear resgate antecipado": tudo o que está em carência passa a disponível
     * agora. Devolve o saldo depois da liberação.
     */
    com.cernecommerce.core.domain.model.cashback.CashbackBalance releasePending(Long customerId);

    /**
     * Cria uma nova taxa de cashback.
     *
     * @throws com.cernecommerce.core.domain.exception.cashback.CashbackRateAlreadyExistsException
     *         se já existe uma taxa ativa e sem {@code validTo} para a mesma abrangência
     */
    CashbackRate createRate(CashbackScope scope, String scopeRef, BigDecimal percent, Instant validFrom,
            Instant validTo);

    /**
     * Altera parcialmente uma taxa — argumento nulo significa "não mexer neste campo".
     *
     * @throws com.cernecommerce.core.domain.exception.cashback.CashbackRateNotFoundException
     *         se o id não existir
     */
    CashbackRate patchRate(Long id, BigDecimal percent, Boolean active, Instant validTo);

    PageResult<CashbackRate> listRates(int page, int size);

    /**
     * Resolve a taxa vigente para o SKU, na cadeia SKU → CATEGORY → GLOBAL.
     *
     * @return {@code null} se nenhuma taxa se aplica (item não ganha cashback)
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductNotFoundException se o SKU
     *         não existir
     */
    CashbackRate resolveApplicableRate(String sku);

    /**
     * Mesma cadeia SKU → CATEGORY → GLOBAL, com a categoria informada e sem ir ao catálogo — para
     * linha que não tem produto, como a sessão do cardápio da mesa (PDV-F021).
     *
     * @return {@code null} se nenhuma taxa se aplica
     */
    CashbackRate resolveApplicableRate(String sku, String category);

    /**
     * Produtos cuja taxa vigente consome mais de {@code maxSharePercent} da margem do item
     * ({@code Pricing.marginPercent()}) — o diagnóstico que evita descobrir um carvão a 8%
     * comendo 44% do lucro só no fechamento do mês.
     */
    List<CashbackMarginImpactItem> findMarginImpact(BigDecimal maxSharePercent);

    /**
     * Lança os ganhos {@code EARNED} de um pedido recém-concluído, um por item com taxa
     * carimbada. Não lança nada para venda sem cliente ou cliente sem CPF (
     * {@code Customer.isOfficiallyRegistered()}) — cliente "leve" não acumula cashback.
     */
    void recordEarnedForOrder(Order order);

    /**
     * CRM-F010 — cashback da parte MARCADA de um pedido, creditado na quitação: a fração do
     * cashback total do pedido proporcional a {@code settledAmount / totalPayable}. Entrada sem
     * item ({@code orderItemId} nulo), uma por quitação; o reembolso do pedido a reverte junto com
     * as demais, porque carrega o {@code orderId}.
     */
    void recordEarnedForReceivablePayment(Order order, java.math.BigDecimal settledAmount);

    /**
     * V173 — desfaz {@link #recordEarnedForReceivablePayment} de uma quitação estornada: uma entrada
     * {@code REVERSED} para o ganho sem item do pedido com o mesmo valor, lançado mais perto de
     * {@code settledAt} (a entrada não guarda o lote). Sem ganho correspondente (cliente sem CPF, ou o
     * reembolso do pedido já reverteu), não faz nada. Se o cliente já resgatou, o saldo pode ficar
     * negativo — o ledger registra o débito em vez de escondê-lo.
     */
    void reverseEarnedForReceivablePayment(Order order, java.math.BigDecimal settledAmount,
            java.time.Instant settledAt);

    /**
     * Reverte os ganhos {@code EARNED} de um pedido reembolsado (PDV-F007): uma entrada
     * {@code REVERSED} por {@code EARNED} ainda não revertido. Não repete a checagem de
     * elegibilidade do cliente — se a entrada {@code EARNED} existe, ela já passou por essa
     * checagem quando foi lançada. Pedido que nunca ganhou cashback (cliente sem CPF, ou venda
     * anônima) simplesmente não tem nada para reverter.
     */
    void reverseEarningsForOrder(Order order);

    /**
     * PED-F004 — reverte só os ganhos {@code EARNED} dos itens informados: os que o estorno parcial
     * acabou de zerar. O ledger reverte uma entrada inteira (a {@code REVERSED} referencia a
     * {@code EARNED}), então o item com parte das unidades estornadas mantém o cashback até a última
     * unidade sair.
     */
    void reverseEarningsForOrderItems(Order order, java.util.Collection<Long> orderItemIds);

    /**
     * Saldo do cliente: disponível, pendente (em carência) e a expirar em breve.
     *
     * @throws com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException se o cliente
     *         não existir
     */
    CashbackBalance getCustomerBalance(Long customerId);

    /** Extrato paginado do cliente, mais recente primeiro. */
    PageResult<CashbackEntry> listCustomerEntries(Long customerId, int page, int size);

    /**
     * Varre ganhos {@code EARNED} vencidos e grava a entrada {@code EXPIRED} correspondente, em
     * lotes de no máximo {@code batchSize}. Chamado pelo scheduler.
     *
     * @return quantos ganhos foram expirados nesta passada
     */
    int expireEntries(int batchSize);

    // ------------------------------------------------------------------------------------------
    // V186 — parâmetros e resgate
    // ------------------------------------------------------------------------------------------

    /** Parâmetros atuais do programa (carência, expiração, teto e mínimo do resgate). */
    CashbackSettings getSettings();

    /** Grava os quatro parâmetros. Carência e expiração novas valem para ganhos novos. */
    CashbackSettings updateSettings(CashbackSettings settings, String username);

    /**
     * Prévia: quanto o cliente pode resgatar numa compra cuja mercadoria (bruto − desconto) vale
     * {@code merchandiseNet}. Nunca lança por regra de resgate — devolve o motivo em {@code reason}.
     */
    CashbackRedemptionQuote quoteRedemption(Long customerId, BigDecimal merchandiseNet);

    /**
     * Confere se {@code requested} pode ser resgatado na compra. Chamado pelos canais antes de
     * aplicar o resgate ao pedido (e de novo, sob trava, em {@link #redeemForOrder}).
     *
     * @throws com.cernecommerce.core.domain.exception.cashback.CashbackRedemptionNotAllowedException
     *         com o código da regra que falhou
     */
    void validateRedemption(Long customerId, BigDecimal merchandiseNet, BigDecimal requested);

    /**
     * Debita do saldo o {@code order.cashbackRedeemed()} do pedido já salvo: grava o
     * {@code REDEEMED} e a alocação FIFO nos ganhos. Sem resgate no pedido, não faz nada. Trava o
     * cliente até o fim da transação — dois resgates simultâneos não gastam o mesmo saldo.
     */
    void redeemForOrder(Order savedOrder);

    /**
     * Devolve ao saldo o resgate do pedido (cancelamento/estorno). {@code amount} nulo devolve
     * tudo o que ainda não foi devolvido; com valor, até esse limite (estorno parcial). A parte
     * que veio de ganho já vencido não volta. Sem resgate no pedido, não faz nada.
     *
     * @return o crédito efetivamente devolvido
     */
    BigDecimal restoreRedemptionForOrder(Order order, BigDecimal amount);
}
