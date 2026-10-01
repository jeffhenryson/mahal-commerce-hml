package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionStaleException;
import com.cernecommerce.core.domain.exception.recebivel.CreditLimitExceededException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerHasOverdueException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerNotEligibleForOnAccountException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerRequiredForOnAccountException;
import com.cernecommerce.core.domain.exception.recebivel.DuplicateOnAccountPaymentException;
import com.cernecommerce.core.domain.exception.recebivel.InvalidDueDateException;
import com.cernecommerce.core.domain.exception.recebivel.PaymentExceedsBalanceException;
import com.cernecommerce.core.domain.exception.recebivel.ReceivableNotOpenException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.OnAccountEligibility;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePayment;
import com.cernecommerce.core.domain.model.recebivel.ReceivablePaymentBatch;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerCreditLimitRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceivableServiceTest {

    /** 30/09/2026 12:00 em São Paulo. */
    private static final Instant NOW = Instant.parse("2026-09-30T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final Long CUSTOMER = 123L;

    @Mock CustomerReceivableRepository receivableRepository;
    @Mock CustomerCreditLimitRepository creditLimitRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CustomerTagRepository customerTagRepository;
    @Mock OrderRepository orderRepository;
    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock SystemConfigPort systemConfigPort;

    ReceivableService service;

    @BeforeEach
    void setUp() {
        service = new ReceivableService(receivableRepository, creditLimitRepository, customerRepository,
                customerTagRepository, orderRepository, cashRegisterRepository, cashbackUseCase, systemConfigPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void givenCustomer(boolean vip) {
        lenient().when(customerRepository.findById(CUSTOMER)).thenReturn(Optional.of(Customer.of(CUSTOMER, "Ana",
                "83999990000", null, "12345678901", "PDV", NOW, CustomerStage.NOVO_LEAD)));
        lenient().when(customerTagRepository.findTagsByCustomerId(CUSTOMER))
                .thenReturn(vip ? List.of(new Tag(1L, " vip ")) : List.of(new Tag(2L, "Frequente")));
    }

    private void givenBalances(String limit, String open, String overdue) {
        lenient().when(creditLimitRepository.findByCustomerId(CUSTOMER))
                .thenReturn(limit == null ? Optional.empty() : Optional.of(new BigDecimal(limit)));
        lenient().when(systemConfigPort.getDecimal(eq(ReceivableService.DEFAULT_CREDIT_LIMIT_KEY), any()))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(receivableRepository.sumOpenBalance(CUSTOMER)).thenReturn(new BigDecimal(open));
        lenient().when(receivableRepository.sumOverdueBalance(CUSTOMER, TODAY)).thenReturn(new BigDecimal(overdue));
    }

    private static List<PaymentCommand> marked(String amount, LocalDate dueDate) {
        return List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("20.00"), null),
                PaymentCommand.onAccount(new BigDecimal(amount), dueDate));
    }

    // ── validateOnAccount ────────────────────────────────────────────────────────────────────

    @Test
    void validate_withoutOnAccountLine_doesNothing() {
        service.validateOnAccount(null, List.of(new PaymentCommand(PaymentMethod.PIX, BigDecimal.TEN, null)));

        verifyNoInteractions(customerRepository, receivableRepository);
    }

    @Test
    void validate_acceptsVipWithinLimit() {
        givenCustomer(true);
        givenBalances("100.00", "50.00", "0");

        service.validateOnAccount(CUSTOMER, marked("50.00", TODAY));
    }

    @Test
    void validate_refusesTwoOnAccountLines() {
        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, List.of(
                PaymentCommand.onAccount(BigDecimal.ONE, TODAY), PaymentCommand.onAccount(BigDecimal.ONE, TODAY))))
                .isInstanceOf(DuplicateOnAccountPaymentException.class);
    }

    @Test
    void validate_refusesMissingOrPastDueDate() {
        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, marked("10.00", null)))
                .isInstanceOf(InvalidDueDateException.class);
        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, marked("10.00", TODAY.minusDays(1))))
                .isInstanceOf(InvalidDueDateException.class);
    }

    @Test
    void validate_refusesAnonymousSale() {
        assertThatThrownBy(() -> service.validateOnAccount(null, marked("10.00", TODAY)))
                .isInstanceOf(CustomerRequiredForOnAccountException.class);
    }

    @Test
    void validate_refusesNonVip() {
        givenCustomer(false);

        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, marked("10.00", TODAY)))
                .isInstanceOf(CustomerNotEligibleForOnAccountException.class);
    }

    @Test
    void validate_refusesCustomerWithOverdue() {
        givenCustomer(true);
        givenBalances("500.00", "40.00", "40.00");

        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, marked("10.00", TODAY)))
                .isInstanceOf(CustomerHasOverdueException.class)
                .satisfies(e -> assertThat(((CustomerHasOverdueException) e).getOverdueBalance())
                        .isEqualByComparingTo("40.00"));
    }

    @Test
    void validate_refusesAboveLimit_withTheNumbersForTheScreen() {
        givenCustomer(true);
        givenBalances("100.00", "80.00", "0");

        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, marked("30.00", TODAY)))
                .isInstanceOf(CreditLimitExceededException.class)
                .satisfies(e -> {
                    CreditLimitExceededException ex = (CreditLimitExceededException) e;
                    assertThat(ex.getLimit()).isEqualByComparingTo("100.00");
                    assertThat(ex.getOpenBalance()).isEqualByComparingTo("80.00");
                    assertThat(ex.getAvailable()).isEqualByComparingTo("20.00");
                });
    }

    @Test
    void validate_defaultLimitZero_requiresAnIndividualLimit() {
        givenCustomer(true);
        givenBalances(null, "0", "0");

        assertThatThrownBy(() -> service.validateOnAccount(CUSTOMER, marked("1.00", TODAY)))
                .isInstanceOf(CreditLimitExceededException.class);
    }

    // ── eligibility ──────────────────────────────────────────────────────────────────────────

    @Test
    void eligibility_listsEveryBlockingReason() {
        givenCustomer(false);
        givenBalances("50.00", "50.00", "10.00");
        when(systemConfigPort.getInt(eq(ReceivableService.DEFAULT_DUE_DAYS_KEY), anyInt())).thenReturn(30);

        OnAccountEligibility e = service.eligibility(CUSTOMER, false);

        assertThat(e.eligible()).isFalse();
        assertThat(e.reasons()).containsExactly("CUSTOMER_NOT_ELIGIBLE", "ON_ACCOUNT_NOT_ALLOWED",
                "CUSTOMER_HAS_OVERDUE", "CREDIT_LIMIT_EXCEEDED");
        assertThat(e.available()).isEqualByComparingTo("0");
        assertThat(e.defaultDueDate()).isEqualTo(TODAY.plusDays(30));
    }

    @Test
    void eligibility_vipWithRoom_isEligible() {
        givenCustomer(true);
        givenBalances("300.00", "120.00", "0");
        when(systemConfigPort.getInt(eq(ReceivableService.DEFAULT_DUE_DAYS_KEY), anyInt())).thenReturn(15);

        OnAccountEligibility e = service.eligibility(CUSTOMER, true);

        assertThat(e.eligible()).isTrue();
        assertThat(e.reasons()).isEmpty();
        assertThat(e.available()).isEqualByComparingTo("180.00");
        assertThat(e.creditLimit()).isEqualByComparingTo("300.00");
    }

    // ── pay ──────────────────────────────────────────────────────────────────────────────────

    private static CustomerReceivable receivable(long id, String amount, String paid, LocalDate due) {
        return new CustomerReceivable(id, CUSTOMER, 900L + id, null, 1L, new BigDecimal(amount),
                new BigDecimal(paid), due, new BigDecimal(paid).signum() > 0 ? ReceivableStatus.PARCIAL
                        : ReceivableStatus.ABERTO, NOW, "ana", null, null, null, null, 0L, List.of());
    }

    private void givenReceivingSession() {
        lenient().when(cashRegisterRepository.findById(45L)).thenReturn(Optional.of(CashRegisterSession.of(45L, "bia",
                NOW.minusSeconds(3600), BigDecimal.ZERO, "LOJA-01", null, null, null, null, null,
                CashRegisterSession.Status.OPEN)));
        givenCustomer(true);
        lenient().when(receivableRepository.saveBatch(any())).thenAnswer(inv -> {
            ReceivablePaymentBatch b = inv.getArgument(0);
            return new ReceivablePaymentBatch(77L, b.customerId(), b.cashSessionId(), b.changeAmount(),
                    b.receivedBy(), b.receivedAt());
        });
        lenient().when(receivableRepository.savePayment(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(receivableRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    void pay_withoutIds_appliesFifoAndLeavesThePartialOnTheLastOne() {
        givenReceivingSession();
        when(receivableRepository.findOpenByCustomerIdForUpdate(CUSTOMER)).thenReturn(List.of(
                receivable(9L, "30.00", "0", TODAY.plusDays(2)),
                receivable(12L, "50.00", "0", TODAY.plusDays(10))));
        when(receivableRepository.sumOpenBalance(CUSTOMER)).thenReturn(new BigDecimal("30.00"));

        ReceivableUseCase.SettlementResult result = service.pay(45L, "bia", CUSTOMER,
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("50.00"), null)), null);

        assertThat(result.batchId()).isEqualTo(77L);
        assertThat(result.changeAmount()).isEqualByComparingTo("0");
        assertThat(result.applied())
                .extracting(ReceivableUseCase.AppliedPayment::receivableId, a -> a.amount().setScale(2),
                        ReceivableUseCase.AppliedPayment::statusAfter)
                .containsExactly(
                        tuple(9L, new BigDecimal("30.00"), ReceivableStatus.QUITADO),
                        tuple(12L, new BigDecimal("20.00"), ReceivableStatus.PARCIAL));
        assertThat(result.openBalanceAfter()).isEqualByComparingTo("30.00");
    }

    @Test
    void pay_cashAboveTheBalance_givesChange() {
        givenReceivingSession();
        when(receivableRepository.findOpenByCustomerIdForUpdate(CUSTOMER)).thenReturn(List.of(
                receivable(9L, "28.00", "0", TODAY)));
        when(receivableRepository.sumOpenBalance(CUSTOMER)).thenReturn(BigDecimal.ZERO);

        ReceivableUseCase.SettlementResult result = service.pay(45L, "bia", CUSTOMER,
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("50.00"), null)), null);

        assertThat(result.changeAmount()).isEqualByComparingTo("22.00");
        ArgumentCaptor<ReceivablePaymentBatch> batch = ArgumentCaptor.forClass(ReceivablePaymentBatch.class);
        verify(receivableRepository).saveBatch(batch.capture());
        assertThat(batch.getValue().changeAmount()).isEqualByComparingTo("22.00");
        assertThat(batch.getValue().cashSessionId()).isEqualTo(45L);
        ArgumentCaptor<ReceivablePayment> line = ArgumentCaptor.forClass(ReceivablePayment.class);
        verify(receivableRepository).savePayment(line.capture());
        assertThat(line.getValue().amount()).isEqualByComparingTo("28.00");
    }

    @Test
    void pay_nonCashAboveTheBalance_isRefused() {
        givenReceivingSession();
        when(receivableRepository.findOpenByCustomerIdForUpdate(CUSTOMER)).thenReturn(List.of(
                receivable(9L, "28.00", "0", TODAY)));

        assertThatThrownBy(() -> service.pay(45L, "bia", CUSTOMER,
                List.of(new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("30.00"), null)), null))
                .isInstanceOf(PaymentExceedsBalanceException.class);
        verify(receivableRepository, never()).saveBatch(any());
    }

    @Test
    void pay_withIds_appliesOnlyThoseInTheGivenOrder() {
        givenReceivingSession();
        when(receivableRepository.findOpenByCustomerIdForUpdate(CUSTOMER)).thenReturn(List.of(
                receivable(9L, "30.00", "0", TODAY.plusDays(2)),
                receivable(12L, "50.00", "10.00", TODAY.plusDays(10))));
        when(receivableRepository.sumOpenBalance(CUSTOMER)).thenReturn(new BigDecimal("30.00"));

        ReceivableUseCase.SettlementResult result = service.pay(45L, "bia", CUSTOMER,
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("40.00"), null)), List.of(12L));

        assertThat(result.applied()).singleElement().satisfies(a -> {
            assertThat(a.receivableId()).isEqualTo(12L);
            assertThat(a.statusAfter()).isEqualTo(ReceivableStatus.QUITADO);
        });
    }

    @Test
    void pay_settledReceivableInTheIds_isRefused() {
        givenReceivingSession();
        when(receivableRepository.findOpenByCustomerIdForUpdate(CUSTOMER)).thenReturn(List.of());
        CustomerReceivable settled = receivable(9L, "30.00", "30.00", TODAY);
        when(receivableRepository.findById(9L)).thenReturn(Optional.of(new CustomerReceivable(9L, CUSTOMER, 909L,
                null, 1L, settled.amount(), settled.amountPaid(), TODAY, ReceivableStatus.QUITADO, NOW, "ana", NOW,
                null, null, null, 0L, List.of())));

        assertThatThrownBy(() -> service.pay(45L, "bia", CUSTOMER,
                List.of(new PaymentCommand(PaymentMethod.PIX, BigDecimal.TEN, null)), List.of(9L)))
                .isInstanceOf(ReceivableNotOpenException.class);
    }

    @Test
    void pay_inASessionFromAPreviousDay_isRefused() {
        when(cashRegisterRepository.findById(45L)).thenReturn(Optional.of(CashRegisterSession.of(45L, "bia",
                NOW.minusSeconds(2 * 24 * 3600), BigDecimal.ZERO, "LOJA-01", null, null, null, null, null,
                CashRegisterSession.Status.OPEN)));

        assertThatThrownBy(() -> service.pay(45L, "bia", CUSTOMER,
                List.of(new PaymentCommand(PaymentMethod.PIX, BigDecimal.TEN, null)), null))
                .isInstanceOf(CashRegisterSessionStaleException.class);
    }

    // ── manutenção ───────────────────────────────────────────────────────────────────────────

    @Test
    void changeDueDate_reopensAnOverdueReceivable() {
        CustomerReceivable overdue = new CustomerReceivable(9L, CUSTOMER, 909L, null, 1L, new BigDecimal("30.00"),
                new BigDecimal("10.00"), TODAY.minusDays(3), ReceivableStatus.VENCIDO, NOW, "ana", null, null, null,
                null, 0L, List.of());
        when(receivableRepository.findById(9L)).thenReturn(Optional.of(overdue));
        when(receivableRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerReceivable updated = service.changeDueDate(9L, TODAY.plusDays(15), "gerente");

        assertThat(updated.status()).isEqualTo(ReceivableStatus.PARCIAL);
        assertThat(updated.dueDate()).isEqualTo(TODAY.plusDays(15));
    }

    @Test
    void cancelOpenForOrder_cancelsOnlyWhenOpen() {
        when(receivableRepository.findByOrderId(909L)).thenReturn(Optional.of(receivable(9L, "30.00", "0", TODAY)));
        when(receivableRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.cancelOpenForOrder(909L, "Pedido reembolsado", "gerente");

        ArgumentCaptor<CustomerReceivable> saved = ArgumentCaptor.forClass(CustomerReceivable.class);
        verify(receivableRepository).save(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(ReceivableStatus.CANCELADO);
        assertThat(saved.getValue().cancelledBy()).isEqualTo("gerente");
    }

    @Test
    void setCreditLimit_nullReturnsToTheDefault() {
        givenCustomer(true);
        when(creditLimitRepository.findByCustomerId(CUSTOMER))
                .thenReturn(Optional.of(new BigDecimal("300.00")), Optional.empty());
        when(systemConfigPort.getDecimal(eq(ReceivableService.DEFAULT_CREDIT_LIMIT_KEY), any()))
                .thenReturn(new BigDecimal("50.00"));

        ReceivableUseCase.CreditLimitChange change = service.setCreditLimit(CUSTOMER, null, "gerente");

        verify(creditLimitRepository).delete(CUSTOMER);
        assertThat(change.before()).isEqualByComparingTo("300.00");
        assertThat(change.after()).isEqualByComparingTo("50.00");
    }

    @Test
    void markOverdue_usesTheStoreDate() {
        when(receivableRepository.markOverdue(TODAY)).thenReturn(3);

        assertThat(service.markOverdue()).isEqualTo(3);
    }
}
