package com.cernecommerce.core.ports.out.recebivel;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.OnAccountChannel;
import com.cernecommerce.core.domain.model.recebivel.ReceivableFilter;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePayment;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePaymentBatch;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Recebíveis do "Marcar" e suas quitações (CRM-F010). */
public interface CustomerReceivableRepository {

    /** Grava o recebível; os itens só são gravados na criação (são snapshot, nunca mudam). */
    CustomerReceivable save(CustomerReceivable receivable);

    Optional<CustomerReceivable> findById(Long id);

    Optional<CustomerReceivable> findByOrderId(Long orderId);

    List<CustomerReceivable> findByOrderIds(Collection<Long> orderIds);

    /** Mais recentes primeiro. */
    List<CustomerReceivable> findByCustomerId(Long customerId);

    /**
     * Os em aberto do cliente, travados para escrita — serializa quitações concorrentes do mesmo
     * cliente. Ordem FIFO: vencimento, depois criação.
     */
    List<CustomerReceivable> findOpenByCustomerIdForUpdate(Long customerId);

    PageResult<CustomerReceivable> findAll(ReceivableFilter filter, LocalDate today, int page, int size);

    BigDecimal sumOpenBalance(Long customerId);

    /** O em aberto do cliente só no canal: BALCAO sem comanda, MESA com comanda. */
    BigDecimal sumOpenBalance(Long customerId, OnAccountChannel channel);

    BigDecimal sumOverdueBalance(Long customerId, LocalDate today);

    /** Agrupado por cliente, sobre os recebíveis nos {@code statuses}. */
    List<CustomerBalanceRow> summarizeByCustomer(Collection<ReceivableStatus> statuses, LocalDate today);

    /** ABERTO/PARCIAL com vencimento antes de {@code today} passam a VENCIDO. Devolve quantos. */
    int markOverdue(LocalDate today);

    ReceivablePaymentBatch saveBatch(ReceivablePaymentBatch batch);

    ReceivablePayment savePayment(ReceivablePayment payment);

    List<ReceivablePayment> findPaymentsByReceivableIds(Collection<Long> receivableIds);

    /** Abatido por quitações recebidas na sessão, no método — já líquido do troco. */
    BigDecimal sumReceivedBySessionAndMethod(Long sessionId, PaymentMethod method);

    record CustomerBalanceRow(Long customerId, BigDecimal openBalance, BigDecimal overdueBalance,
            LocalDate nextDueDate, long count, BigDecimal openBalanceBalcao, BigDecimal openBalanceMesa) {
    }
}
