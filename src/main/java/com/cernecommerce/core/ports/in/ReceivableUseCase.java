package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.OnAccountEligibility;
import com.cernecommerce.core.domain.model.recebivel.ReceivableCustomerSummary;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePayment;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * "Marcar" — venda a prazo para cliente VIP (CRM-F010).
 *
 * <p>A venda (balcão ou mesa) aceita uma linha {@code MARCADO}: a parte marcada não entra no caixa e
 * vira um {@link CustomerReceivable}. A quitação entra no caixa de quem recebe.</p>
 */
public interface ReceivableUseCase {

    // ── Na venda ─────────────────────────────────────────────────────────────────────────────

    /**
     * Valida a linha MARCADO de uma venda, ANTES de qualquer escrita. Sem linha MARCADO, não faz
     * nada. A permissão do operador ({@code PDV_SALE_ON_ACCOUNT}) é checada na borda.
     *
     * @throws com.cernecommerce.core.domain.exception.recebivel.DuplicateOnAccountPaymentException
     * @throws com.cernecommerce.core.domain.exception.recebivel.InvalidDueDateException
     * @throws com.cernecommerce.core.domain.exception.recebivel.CustomerRequiredForOnAccountException
     * @throws com.cernecommerce.core.domain.exception.recebivel.CustomerNotEligibleForOnAccountException
     * @throws com.cernecommerce.core.domain.exception.recebivel.CustomerHasOverdueException
     * @throws com.cernecommerce.core.domain.exception.recebivel.CreditLimitExceededException
     */
    void validateOnAccount(Long customerId, List<PaymentCommand> payments);

    /** Cria o recebível do pedido recém-concluído, na mesma transação da venda. */
    CustomerReceivable createFromOrder(Order order, Long comandaId, PaymentCommand onAccountLine, String username);

    // ── Consulta ─────────────────────────────────────────────────────────────────────────────

    OnAccountEligibility eligibility(Long customerId, boolean operatorMayMark);

    PageResult<ReceivableView> list(ReceivableFilter filter, int page, int size);

    List<ReceivableCustomerSummary> summary(ReceivableStatus status, Boolean overdue);

    ReceivableView get(Long id);

    List<ReceivableView> listByCustomer(Long customerId);

    Optional<CustomerReceivable> findByOrderId(Long orderId);

    CustomerBalance balance(Long customerId);

    // ── Quitação ─────────────────────────────────────────────────────────────────────────────

    /**
     * Recebe um marcado no caixa de {@code username} ({@code sessionId}, aberto, dele e de hoje).
     * Sem {@code receivableIds}, abate FIFO por vencimento; com eles, só neles e na ordem dada. Só
     * DINHEIRO pode exceder o saldo (vira troco).
     */
    SettlementResult pay(Long sessionId, String username, Long customerId, List<PaymentCommand> payments,
            List<Long> receivableIds);

    // ── Manutenção ───────────────────────────────────────────────────────────────────────────

    CustomerReceivable changeDueDate(Long id, LocalDate dueDate, String username);

    CustomerReceivable cancel(Long id, String reason, String username);

    /** Reembolso/cancelamento do pedido de origem cancela o recebível em aberto. */
    void cancelOpenForOrder(Long orderId, String reason, String username);

    /** Job diário: ABERTO/PARCIAL vencidos passam a VENCIDO. */
    int markOverdue();

    /** {@code null} volta ao limite padrão. Devolve o limite efetivo antes e depois. */
    CreditLimitChange setCreditLimit(Long customerId, BigDecimal creditLimit, String username);

    // ── Caixa ────────────────────────────────────────────────────────────────────────────────

    /** Abatido por quitações recebidas na sessão, no método — já líquido do troco. */
    BigDecimal receivedInSession(Long sessionId, PaymentMethod method);

    // ── Tipos ────────────────────────────────────────────────────────────────────────────────

    record ReceivableView(CustomerReceivable receivable, String customerName, String orderNumber,
            String tableLabel, long daysOverdue, List<ReceivablePayment> payments) {
    }

    record CustomerBalance(BigDecimal creditLimit, BigDecimal openBalance, BigDecimal overdueBalance) {
    }

    record AppliedPayment(Long receivableId, BigDecimal amount, ReceivableStatus statusAfter) {
    }

    record SettlementResult(Long batchId, List<AppliedPayment> applied, BigDecimal changeAmount,
            BigDecimal openBalanceAfter) {
    }

    record CreditLimitChange(BigDecimal before, BigDecimal after) {
    }
}
