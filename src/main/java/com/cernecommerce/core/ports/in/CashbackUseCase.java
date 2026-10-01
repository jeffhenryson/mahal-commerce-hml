package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Caso de uso do programa de cashback (CRM-F003, §2.4 do plano de arquitetura).
 *
 * <p>Esta fatia cobre <b>ganhar</b> cashback (taxa por abrangência, ledger, lançamento na
 * conclusão da venda, expiração) e as consultas de saldo/extrato/diagnóstico de margem.
 * Resgate no balcão ({@code CASHBACK_REDEEM}) e ajuste manual ({@code CASHBACK_ADJUST}) ficam
 * para uma fatia seguinte. A reversão por reembolso ({@link #reverseEarningsForOrder}) já entrou,
 * via Fatia 5 (PDV-F007) — é o {@code OrderService.refundOrder} que a aciona.</p>
 */
public interface CashbackUseCase {

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
     * Reverte os ganhos {@code EARNED} de um pedido reembolsado (PDV-F007): uma entrada
     * {@code REVERSED} por {@code EARNED} ainda não revertido. Não repete a checagem de
     * elegibilidade do cliente — se a entrada {@code EARNED} existe, ela já passou por essa
     * checagem quando foi lançada. Pedido que nunca ganhou cashback (cliente sem CPF, ou venda
     * anônima) simplesmente não tem nada para reverter.
     */
    void reverseEarningsForOrder(Order order);

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
}
