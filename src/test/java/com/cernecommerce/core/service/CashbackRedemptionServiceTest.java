package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.cashback.CashbackRedemptionNotAllowedException;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionAllocation;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionQuote;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;
import com.cernecommerce.core.domain.model.cashback.RedeemableEarning;
import com.cernecommerce.core.domain.model.config.SystemConfig;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** V186 — resgate de cashback: regras, alocação FIFO, devolução, expiração parcial e parâmetros. */
@ExtendWith(MockitoExtension.class)
class CashbackRedemptionServiceTest {

    private static final Long CUSTOMER = 42L;

    @Mock CashbackRateRepository cashbackRateRepository;
    @Mock CashbackEntryRepository cashbackEntryRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CustomerRepository customerRepository;
    @Mock SystemConfigPort systemConfigPort;
    @Mock OrderPaymentRepository orderPaymentRepository;

    CashbackService service;

    @BeforeEach
    void setUp() {
        service = new CashbackService(cashbackRateRepository, cashbackEntryRepository, estoqueUseCase,
                customerRepository, systemConfigPort, orderPaymentRepository);
        // Parâmetros padrão: carência 7, expiração 180, teto 50%, mínimo R$ 50.
        lenient().when(systemConfigPort.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient().when(systemConfigPort.getDecimal(anyString(), any())).thenAnswer(inv -> inv.getArgument(1));
    }

    // ── Prévia e validação ──────────────────────────────────────────────────────────────────

    @Test
    void quote_maxIsTheSmallerOfBalanceAndCap() {
        registered();
        balance("80.00", earning(1L, "80.00", 30));

        CashbackRedemptionQuote quote = service.quoteRedemption(CUSTOMER, new BigDecimal("100.00"));

        assertThat(quote.reason()).isNull();
        assertThat(quote.capAmount()).isEqualByComparingTo("50.00");
        assertThat(quote.maxRedeemable()).isEqualByComparingTo("50.00");
    }

    @Test
    void quote_customerWithoutCpfIsNotEligible() {
        when(customerRepository.findById(CUSTOMER)).thenReturn(Optional.of(Customer.of(CUSTOMER, "Leve",
                "11999999999", null, null, "balcao", Instant.now(), CustomerStage.NOVO_LEAD)));
        lenient().when(cashbackEntryRepository.sumAvailableByCustomerId(eq(CUSTOMER), any())).thenReturn(BigDecimal.ZERO);

        CashbackRedemptionQuote quote = service.quoteRedemption(CUSTOMER, new BigDecimal("100.00"));

        assertThat(quote.reason()).isEqualTo(CashbackRedemptionNotAllowedException.CUSTOMER_NOT_ELIGIBLE);
        assertThat(quote.maxRedeemable()).isEqualByComparingTo("0");
    }

    @Test
    void quote_balanceBelowMinimumCannotRedeem() {
        registered();
        balance("49.99", earning(1L, "49.99", 30));

        CashbackRedemptionQuote quote = service.quoteRedemption(CUSTOMER, new BigDecimal("100.00"));

        assertThat(quote.reason()).isEqualTo(CashbackRedemptionNotAllowedException.BELOW_MINIMUM_BALANCE);
        assertThat(quote.maxRedeemable()).isEqualByComparingTo("0");
    }

    @Test
    void quote_zeroCapDisablesRedemption() {
        registered();
        balance("80.00", earning(1L, "80.00", 30));
        lenient().when(systemConfigPort.getDecimal(eq(CashbackSettings.KEY_RESGATE_TETO_PERCENT), any()))
                .thenReturn(BigDecimal.ZERO);

        assertThat(service.quoteRedemption(CUSTOMER, new BigDecimal("100.00")).reason())
                .isEqualTo(CashbackRedemptionNotAllowedException.REDEMPTION_DISABLED);
    }

    @Test
    void validate_refusesAboveCap() {
        registered();
        balance("80.00", earning(1L, "80.00", 30));

        assertThatThrownBy(() -> service.validateRedemption(CUSTOMER, new BigDecimal("100.00"), new BigDecimal("50.01")))
                .isInstanceOf(CashbackRedemptionNotAllowedException.class)
                .extracting("code").isEqualTo(CashbackRedemptionNotAllowedException.ABOVE_CAP);
    }

    @Test
    void validate_refusesAboveBalance() {
        registered();
        balance("60.00", earning(1L, "60.00", 30));

        assertThatThrownBy(() -> service.validateRedemption(CUSTOMER, new BigDecimal("1000.00"), new BigDecimal("60.01")))
                .isInstanceOf(CashbackRedemptionNotAllowedException.class)
                .extracting("code").isEqualTo(CashbackRedemptionNotAllowedException.INSUFFICIENT_BALANCE);
    }

    @Test
    void validate_refusesWithoutCustomer() {
        assertThatThrownBy(() -> service.validateRedemption(null, new BigDecimal("100.00"), BigDecimal.ONE))
                .isInstanceOf(CashbackRedemptionNotAllowedException.class)
                .extracting("code").isEqualTo(CashbackRedemptionNotAllowedException.CUSTOMER_REQUIRED);
    }

    @Test
    void validate_ledgerDebtLowersTheRedeemableBalance() {
        // Ganhos vivos somam 80, mas um ganho revertido depois de resgatado deixou o ledger em 55.
        registered();
        balance("55.00", earning(1L, "80.00", 30));

        assertThatThrownBy(() -> service.validateRedemption(CUSTOMER, new BigDecimal("1000.00"), new BigDecimal("56.00")))
                .extracting("code").isEqualTo(CashbackRedemptionNotAllowedException.INSUFFICIENT_BALANCE);
    }

    // ── Resgate ─────────────────────────────────────────────────────────────────────────────

    @Test
    void redeemForOrder_consumesEarningsFifoAndLocksTheCustomer() {
        registered();
        balance("80.00", earning(1L, "30.00", 10), earning(2L, "50.00", 90));
        when(cashbackEntryRepository.findRedeemedByOrderId(500L)).thenReturn(Optional.empty());
        when(cashbackEntryRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 900L));

        service.redeemForOrder(orderRedeeming("40.00"));

        verify(cashbackEntryRepository).lockCustomer(CUSTOMER);
        ArgumentCaptor<CashbackEntry> entry = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(entry.capture());
        assertThat(entry.getValue().type()).isEqualTo(CashbackEntryType.REDEEMED);
        assertThat(entry.getValue().amount()).isEqualByComparingTo("-40.00");
        assertThat(entry.getValue().orderId()).isEqualTo(500L);

        ArgumentCaptor<CashbackRedemptionAllocation> alloc = ArgumentCaptor.forClass(CashbackRedemptionAllocation.class);
        verify(cashbackEntryRepository, times(2)).saveAllocation(alloc.capture());
        assertThat(alloc.getAllValues())
                .extracting(CashbackRedemptionAllocation::entryId, CashbackRedemptionAllocation::earnedEntryId,
                        a -> a.amount().toPlainString())
                .containsExactly(tuple(900L, 1L, "30.00"), tuple(900L, 2L, "10.00"));
    }

    @Test
    void redeemForOrder_doesNothingWithoutRedemption() {
        service.redeemForOrder(orderRedeeming("0"));

        verifyNoInteractions(cashbackEntryRepository);
    }

    @Test
    void redeemForOrder_isIdempotentPerOrder() {
        when(cashbackEntryRepository.findRedeemedByOrderId(500L)).thenReturn(Optional.of(
                CashbackEntry.redeemed(CUSTOMER, 500L, new BigDecimal("40.00"), Instant.now())));

        service.redeemForOrder(orderRedeeming("40.00"));

        verify(cashbackEntryRepository, never()).save(any());
    }

    // ── Devolução ───────────────────────────────────────────────────────────────────────────

    @Test
    void restore_returnsToLatestExpiringFirstAndSkipsExpiredEarnings() {
        CashbackEntry redeemed = redeemedEntry("40.00");
        when(cashbackEntryRepository.findRedeemedByOrderId(500L)).thenReturn(Optional.of(redeemed));
        when(cashbackEntryRepository.sumRedemptionReversed(900L)).thenReturn(BigDecimal.ZERO);
        // 15 saíram de um ganho já vencido, 25 de um vivo.
        CashbackEntry vencido = earningEntry(1L, "15.00", -1);
        CashbackEntry vivo = earningEntry(2L, "50.00", 60);
        when(cashbackEntryRepository.sumNetAllocationByRedemption(900L))
                .thenReturn(Map.of(1L, new BigDecimal("15.00"), 2L, new BigDecimal("25.00")));
        when(cashbackEntryRepository.findAllById(any())).thenReturn(List.of(vencido, vivo));
        when(cashbackEntryRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 901L));

        BigDecimal credit = service.restoreRedemptionForOrder(orderRedeeming("40.00"), null);

        assertThat(credit).isEqualByComparingTo("25.00");
        ArgumentCaptor<CashbackEntry> entry = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(entry.capture());
        assertThat(entry.getValue().type()).isEqualTo(CashbackEntryType.REDEMPTION_REVERSED);
        assertThat(entry.getValue().amount()).isEqualByComparingTo("25.00");
        assertThat(entry.getValue().reversesEntryId()).isEqualTo(900L);
        ArgumentCaptor<CashbackRedemptionAllocation> alloc = ArgumentCaptor.forClass(CashbackRedemptionAllocation.class);
        verify(cashbackEntryRepository).saveAllocation(alloc.capture());
        assertThat(alloc.getValue().earnedEntryId()).isEqualTo(2L);
        assertThat(alloc.getValue().amount()).isEqualByComparingTo("-25.00");
    }

    @Test
    void restore_partialIsLimitedToWhatIsStillOutstanding() {
        CashbackEntry redeemed = redeemedEntry("40.00");
        when(cashbackEntryRepository.findRedeemedByOrderId(500L)).thenReturn(Optional.of(redeemed));
        when(cashbackEntryRepository.sumRedemptionReversed(900L)).thenReturn(new BigDecimal("35.00"));
        when(cashbackEntryRepository.sumNetAllocationByRedemption(900L)).thenReturn(Map.of(2L, new BigDecimal("5.00")));
        when(cashbackEntryRepository.findAllById(any())).thenReturn(List.of(earningEntry(2L, "50.00", 60)));
        when(cashbackEntryRepository.save(any())).thenAnswer(inv -> withId(inv.getArgument(0), 902L));

        BigDecimal credit = service.restoreRedemptionForOrder(orderRedeeming("40.00"), new BigDecimal("20.00"));

        assertThat(credit).isEqualByComparingTo("5.00");
    }

    @Test
    void restore_doesNothingWhenOrderHadNoRedemption() {
        when(cashbackEntryRepository.findRedeemedByOrderId(500L)).thenReturn(Optional.empty());

        assertThat(service.restoreRedemptionForOrder(orderRedeeming("0"), null)).isEqualByComparingTo("0");
        verify(cashbackEntryRepository, never()).save(any());
    }

    // ── Expiração parcial ───────────────────────────────────────────────────────────────────

    @Test
    void expireEntries_expiresOnlyTheUnconsumedRemainder() {
        CashbackEntry earned = earningEntry(5L, "10.00", -1);
        when(cashbackEntryRepository.findEarnedPendingExpiry(any(), eq(50))).thenReturn(List.of(earned));
        when(cashbackEntryRepository.sumConsumedByEarnedIds(List.of(5L))).thenReturn(Map.of(5L, new BigDecimal("4.00")));

        assertThat(service.expireEntries(50)).isEqualTo(1);

        ArgumentCaptor<CashbackEntry> entry = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(entry.capture());
        assertThat(entry.getValue().type()).isEqualTo(CashbackEntryType.EXPIRED);
        assertThat(entry.getValue().amount()).isEqualByComparingTo("-6.00");
        verify(cashbackEntryRepository).lockCustomer(CUSTOMER);
    }

    @Test
    void expireEntries_skipsFullyConsumedEarning() {
        CashbackEntry earned = earningEntry(5L, "10.00", -1);
        when(cashbackEntryRepository.findEarnedPendingExpiry(any(), eq(50))).thenReturn(List.of(earned));
        when(cashbackEntryRepository.sumConsumedByEarnedIds(List.of(5L))).thenReturn(Map.of(5L, new BigDecimal("10.00")));

        assertThat(service.expireEntries(50)).isZero();
        verify(cashbackEntryRepository, never()).save(any());
    }

    // ── Ganho sobre o líquido pago ──────────────────────────────────────────────────────────

    @Test
    void recordEarnedForOrder_doesNotEarnOnThePartPaidWithCashback() {
        registered();
        when(cashbackEntryRepository.existsEarnedForOrder(500L)).thenReturn(false);
        when(orderPaymentRepository.findByOrderId(500L)).thenReturn(List.of());

        // Item de R$ 100 a 10%, R$ 40 pagos com cashback: ganha 10% de 60 = R$ 6.
        service.recordEarnedForOrder(orderRedeeming("40.00"));

        ArgumentCaptor<CashbackEntry> entry = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(entry.capture());
        assertThat(entry.getValue().type()).isEqualTo(CashbackEntryType.EARNED);
        assertThat(entry.getValue().amount()).isEqualByComparingTo("6.00");
    }

    // ── Parâmetros ──────────────────────────────────────────────────────────────────────────

    @Test
    void updateSettings_writesTheFourKeys() {
        service.updateSettings(new CashbackSettings(3, 90, new BigDecimal("30"), new BigDecimal("20")), "admin");

        ArgumentCaptor<SystemConfig> saved = ArgumentCaptor.forClass(SystemConfig.class);
        verify(systemConfigPort, times(4)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(SystemConfig::key, SystemConfig::value, SystemConfig::updatedBy)
                .containsExactly(
                        tuple(CashbackSettings.KEY_CARENCIA_DIAS, "3", "admin"),
                        tuple(CashbackSettings.KEY_EXPIRACAO_DIAS, "90", "admin"),
                        tuple(CashbackSettings.KEY_RESGATE_TETO_PERCENT, "30", "admin"),
                        tuple(CashbackSettings.KEY_RESGATE_MINIMO_SALDO, "20.00", "admin"));
    }

    @Test
    void getSettings_readsDefaultsWhenKeysAreMissing() {
        CashbackSettings settings = service.getSettings();

        assertThat(settings).isEqualTo(CashbackSettings.defaults());
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────

    private void registered() {
        when(customerRepository.findById(CUSTOMER)).thenReturn(Optional.of(Customer.of(CUSTOMER, "Oficial",
                "11999999999", null, "12345678900", "balcao", Instant.now(), CustomerStage.NOVO_LEAD)));
    }

    private void balance(String ledger, RedeemableEarning... live) {
        lenient().when(cashbackEntryRepository.sumAvailableByCustomerId(eq(CUSTOMER), any()))
                .thenReturn(new BigDecimal(ledger));
        lenient().when(cashbackEntryRepository.findRedeemableEarned(eq(CUSTOMER), any())).thenReturn(List.of(live));
    }

    private static RedeemableEarning earning(Long id, String remaining, int daysToExpire) {
        return new RedeemableEarning(earningEntry(id, remaining, daysToExpire), new BigDecimal(remaining));
    }

    private static CashbackEntry earningEntry(Long id, String amount, int daysToExpire) {
        Instant now = Instant.now();
        Instant expires = now.plus(daysToExpire, ChronoUnit.DAYS);
        return CashbackEntry.of(id, CUSTOMER, 77L, null, CashbackEntryType.EARNED, new BigDecimal(amount),
                expires.minus(200, ChronoUnit.DAYS), expires, null, expires.minus(200, ChronoUnit.DAYS));
    }

    private static CashbackEntry redeemedEntry(String amount) {
        return CashbackEntry.of(900L, CUSTOMER, 500L, null, CashbackEntryType.REDEEMED,
                new BigDecimal(amount).negate(), Instant.now(), null, null, Instant.now());
    }

    private static CashbackEntry withId(CashbackEntry e, Long id) {
        return CashbackEntry.of(id, e.customerId(), e.orderId(), e.orderItemId(), e.type(), e.amount(),
                e.availableAt(), e.expiresAt(), e.reversesEntryId(), e.createdAt());
    }

    /** Pedido de R$ 100 (item a 10% de cashback), pago em parte com {@code redeemed} de cashback. */
    private static Order orderRedeeming(String redeemed) {
        OrderItem item = OrderItem.of(9L, "CARV-001", BigDecimal.ONE, new BigDecimal("100.00"),
                new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("10.0"));
        Instant paidAt = Instant.parse("2026-07-29T12:00:00Z");
        BigDecimal r = new BigDecimal(redeemed);
        return Order.of(500L, "000000500", SalesChannel.BALCAO, OrderStatus.CONCLUIDO, CUSTOMER, 1L,
                "LOJA-01", List.of(item), new BigDecimal("100.00"), BigDecimal.ZERO, r,
                new BigDecimal("100.00").subtract(r), null, null, paidAt, paidAt, paidAt, null, null, 0L);
    }
}
