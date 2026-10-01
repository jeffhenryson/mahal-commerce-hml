package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.cashback.CashbackRateAlreadyExistsException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRateNotFoundException;
import com.cernecommerce.core.domain.exception.crm.CustomerNotFoundException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.out.SystemConfigPort;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashbackServiceTest {

    @Mock CashbackRateRepository cashbackRateRepository;
    @Mock CashbackEntryRepository cashbackEntryRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CustomerRepository customerRepository;
    @Mock SystemConfigPort systemConfigPort;
    @Mock com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository orderPaymentRepository;

    CashbackService cashbackService;

    @BeforeEach
    void setUp() {
        cashbackService = new CashbackService(cashbackRateRepository, cashbackEntryRepository, estoqueUseCase,
                customerRepository, systemConfigPort, orderPaymentRepository);
    }

    // ── Taxas ────────────────────────────────────────────────────────────────────────────────

    @Test
    void createRate_refusesWhenAnActiveOpenEndedRateAlreadyExistsForTheSameScope() {
        when(cashbackRateRepository.existsActiveOpenEnded(CashbackScope.GLOBAL, null)).thenReturn(true);

        assertThatThrownBy(() -> cashbackService.createRate(CashbackScope.GLOBAL, null, BigDecimal.TEN, null, null))
                .isInstanceOf(CashbackRateAlreadyExistsException.class);
        verify(cashbackRateRepository, never()).save(any());
    }

    @Test
    void createRate_savesWhenNoConflict() {
        when(cashbackRateRepository.existsActiveOpenEnded(CashbackScope.SKU, "CARV-001")).thenReturn(false);
        when(cashbackRateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashbackRate created = cashbackService.createRate(CashbackScope.SKU, "CARV-001",
                new BigDecimal("2.5"), null, null);

        assertThat(created.scope()).isEqualTo(CashbackScope.SKU);
        assertThat(created.scopeRef()).isEqualTo("CARV-001");
        assertThat(created.percent()).isEqualByComparingTo("2.5");
    }

    @Test
    void patchRate_throwsWhenNotFound() {
        when(cashbackRateRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashbackService.patchRate(1L, BigDecimal.TEN, null, null))
                .isInstanceOf(CashbackRateNotFoundException.class);
    }

    @Test
    void patchRate_keepsFieldsNotInformed() {
        CashbackRate current = CashbackRate.global(new BigDecimal("3.0"));
        when(cashbackRateRepository.findById(1L)).thenReturn(Optional.of(current));
        when(cashbackRateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CashbackRate updated = cashbackService.patchRate(1L, new BigDecimal("5.0"), null, null);

        assertThat(updated.percent()).isEqualByComparingTo("5.0");
        assertThat(updated.active()).isEqualTo(current.active());
    }

    @Test
    void resolveApplicableRate_asksTheCatalogForTheProductCategory() {
        Product product = Product.of(1L, "CARV-001", "Carvão", "carvao", true, List.of(), Pricing.empty());
        when(estoqueUseCase.findProductBySku("CARV-001")).thenReturn(product);
        CashbackRate rate = CashbackRate.forCategory("carvao", new BigDecimal("2.5"));
        when(cashbackRateRepository.findApplicable(eq("CARV-001"), eq("carvao"), any())).thenReturn(Optional.of(rate));

        CashbackRate resolved = cashbackService.resolveApplicableRate("CARV-001");

        assertThat(resolved).isEqualTo(rate);
    }

    @Test
    void resolveApplicableRate_returnsNullWhenNothingMatches() {
        Product product = Product.of(1L, "CARV-001", "Carvão", "carvao", true, List.of(), Pricing.empty());
        when(estoqueUseCase.findProductBySku("CARV-001")).thenReturn(product);
        when(cashbackRateRepository.findApplicable(eq("CARV-001"), eq("carvao"), any())).thenReturn(Optional.empty());

        assertThat(cashbackService.resolveApplicableRate("CARV-001")).isNull();
    }

    // ── Impacto na margem ───────────────────────────────────────────────────────────────────

    @Test
    void findMarginImpact_includesProductWhoseShareIsAboveTheMaxSharePercent() {
        Product product = Product.of(1L, "CARV-001", "Carvão", "carvao", true, List.of(), Pricing.empty());
        when(estoqueUseCase.listProducts(0, 200))
                .thenReturn(new PageResult<>(List.of(product), 0, 200, 1, 1));
        // custo 80 / venda 100 -> margem 20%.
        when(estoqueUseCase.findPricingBySku("CARV-001"))
                .thenReturn(Pricing.of(new BigDecimal("80.00"), null, new BigDecimal("100.00")));
        CashbackRate rate = CashbackRate.forSku("CARV-001", new BigDecimal("10"));
        when(cashbackRateRepository.findApplicable(eq("CARV-001"), eq("carvao"), any()))
                .thenReturn(Optional.of(rate));

        // share = 10 / 20 * 100 = 50%, acima do teto de 30%.
        List<CashbackMarginImpactItem> result = cashbackService.findMarginImpact(new BigDecimal("30"));

        assertThat(result).hasSize(1);
        CashbackMarginImpactItem item = result.get(0);
        assertThat(item.sku()).isEqualTo("CARV-001");
        assertThat(item.marginPercent()).isEqualByComparingTo("20.00");
        assertThat(item.cashbackPercent()).isEqualByComparingTo("10");
        assertThat(item.marginShareConsumed()).isEqualByComparingTo("50.00");
    }

    @Test
    void findMarginImpact_excludesProductWhoseShareIsBelowTheMaxSharePercent() {
        Product product = Product.of(2L, "ESSE-001", "Essência", "essencia", true, List.of(), Pricing.empty());
        when(estoqueUseCase.listProducts(0, 200))
                .thenReturn(new PageResult<>(List.of(product), 0, 200, 1, 1));
        // custo 50 / venda 100 -> margem 50%.
        when(estoqueUseCase.findPricingBySku("ESSE-001"))
                .thenReturn(Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        CashbackRate rate = CashbackRate.forSku("ESSE-001", new BigDecimal("5"));
        when(cashbackRateRepository.findApplicable(eq("ESSE-001"), eq("essencia"), any()))
                .thenReturn(Optional.of(rate));

        // share = 5 / 50 * 100 = 10%, abaixo do teto de 30%.
        List<CashbackMarginImpactItem> result = cashbackService.findMarginImpact(new BigDecimal("30"));

        assertThat(result).isEmpty();
    }

    @Test
    void findMarginImpact_forAKit_usesThePricingResolvedByFindPricingBySku_notTheProductsRawPricing() {
        // Product.pricing() de um kit vem sempre vazio (o costPrice cru é nulo — só é derivado na
        // leitura, ver comentário de CashbackService.findMarginImpact). Se o serviço usasse
        // product.pricing() direto em vez de estoqueUseCase.findPricingBySku(sku), marginPercent()
        // seria null e o item seria pulado (continue) — o resultado ficaria vazio. Ele não fica:
        // prova que o cálculo usa a Pricing resolvida via findPricingBySku.
        Product kit = Product.of(3L, "KIT-001", "Kit Carvão + Essência", "kits", true, List.of(),
                Pricing.empty(), ProductType.KIT);
        when(estoqueUseCase.listProducts(0, 200))
                .thenReturn(new PageResult<>(List.of(kit), 0, 200, 1, 1));
        // custo 60 / venda 100 -> margem 40%, só visível via findPricingBySku.
        when(estoqueUseCase.findPricingBySku("KIT-001"))
                .thenReturn(Pricing.of(new BigDecimal("60.00"), null, new BigDecimal("100.00")));
        CashbackRate rate = CashbackRate.forSku("KIT-001", new BigDecimal("20"));
        when(cashbackRateRepository.findApplicable(eq("KIT-001"), eq("kits"), any()))
                .thenReturn(Optional.of(rate));

        List<CashbackMarginImpactItem> result = cashbackService.findMarginImpact(new BigDecimal("10"));

        assertThat(result).hasSize(1);
        CashbackMarginImpactItem item = result.get(0);
        assertThat(item.sku()).isEqualTo("KIT-001");
        assertThat(item.marginPercent()).isEqualByComparingTo("40.00");
        assertThat(item.marginShareConsumed()).isEqualByComparingTo("50.00");
    }

    // ── Ganho ────────────────────────────────────────────────────────────────────────────────

    @Test
    void recordEarnedForOrder_doesNothingForAnonymousSale() {
        cashbackService.recordEarnedForOrder(orderWithCustomer(null));

        verifyNoInteractions(customerRepository, cashbackEntryRepository);
    }

    @Test
    void recordEarnedForOrder_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashbackService.recordEarnedForOrder(orderWithCustomer(42L)))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void recordEarnedForOrder_doesNothingForCustomerWithoutCpf() {
        when(customerRepository.findById(42L)).thenReturn(Optional.of(lightCustomer()));

        cashbackService.recordEarnedForOrder(orderWithCustomer(42L));

        verifyNoInteractions(cashbackEntryRepository);
    }

    @Test
    void recordEarnedForOrder_writesEarnedEntryUsingConfiguredGraceAndExpiry() {
        Order order = orderWithCustomer(42L);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(officialCustomer()));
        when(systemConfigPort.getInt("cashback.carencia.dias", 7)).thenReturn(7);
        when(systemConfigPort.getInt("cashback.expiracao.dias", 180)).thenReturn(180);

        cashbackService.recordEarnedForOrder(order);

        ArgumentCaptor<CashbackEntry> captor = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(captor.capture());
        CashbackEntry entry = captor.getValue();
        assertThat(entry.type()).isEqualTo(CashbackEntryType.EARNED);
        assertThat(entry.customerId()).isEqualTo(42L);
        assertThat(entry.orderId()).isEqualTo(order.id());
        assertThat(entry.amount()).isEqualByComparingTo("10.00");
        Instant expectedAvailableAt = order.paidAt().plus(7, ChronoUnit.DAYS);
        assertThat(entry.availableAt()).isEqualTo(expectedAvailableAt);
        assertThat(entry.expiresAt()).isEqualTo(expectedAvailableAt.plus(180, ChronoUnit.DAYS));
    }

    @Test
    void recordEarnedForOrder_isIdempotentWhenAnEarnedEntryAlreadyExistsForTheOrder() {
        // Guarda de idempotência (achado de CashbackLedgerConcurrencyIT): uma segunda chamada
        // para o MESMO pedido não pode duplicar o ganho, mesmo fora de qualquer cenário de
        // concorrência real — existsEarnedForOrder=true já basta para não escrever de novo.
        Order order = orderWithCustomer(42L);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(officialCustomer()));
        when(cashbackEntryRepository.existsEarnedForOrder(order.id())).thenReturn(true);

        cashbackService.recordEarnedForOrder(order);

        verify(cashbackEntryRepository, never()).save(any());
    }

    @Test
    void recordEarnedForOrder_skipsItemsWithoutAStampedRate() {
        OrderItem noRate = OrderItem.of(9L, "SEM-TAXA", BigDecimal.ONE, new BigDecimal("50.00"),
                new BigDecimal("40.00"), BigDecimal.ZERO, null);
        Instant paidAt = Instant.parse("2026-07-29T12:00:00Z");
        Order order = Order.of(1L, "000000001", SalesChannel.BALCAO, OrderStatus.CONCLUIDO, 42L, 1L,
                "LOJA-01", List.of(noRate), new BigDecimal("50.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("50.00"), null, null, paidAt, paidAt, paidAt, null, null, 0L);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(officialCustomer()));

        cashbackService.recordEarnedForOrder(order);

        // Não verifyNoInteractions: a guarda de idempotência (existsEarnedForOrder) roda antes do
        // loop de itens, então o repositório É consultado — só o save() é que não deve acontecer.
        verify(cashbackEntryRepository, never()).save(any());
    }

    @Test
    void recordEarnedForOrder_calledTwiceForTheSameOrder_duplicatesTheEarnedEntry() {
        // Versão sequencial e barata de CashbackLedgerConcurrencyIT (sem threads, determinística).
        // A proteção real de recordEarnedForOrder tem duas camadas: o lock em memória por
        // orderId (só vale dentro desta instância) e a checagem existsEarnedForOrder logo dentro
        // dele — que só barra a segunda chamada se o repositório já enxergar a gravação da
        // primeira (é o que recordEarnedForOrder_isIdempotentWhenAnEarnedEntryAlreadyExistsForTheOrder
        // já cobre, estipulando existsEarnedForOrder=true). Aqui, de propósito, NÃO estipulamos
        // existsEarnedForOrder: o mock não é um banco, não fica "true" sozinho depois do primeiro
        // save(), então a segunda chamada passa pelo mesmo caminho da primeira. Isso documenta a
        // lacuna que motivou a defesa em profundidade de
        // V80__cashback_entry_earned_idempotent.sql (índice único parcial em hml/prod): nada NESTE
        // método, sozinho, impede a duplicação além dessa consulta refletir a escrita anterior —
        // se ela não refletir (leitura stale, outra instância, ou aqui, uma consulta que não foi
        // configurada para "lembrar"), o ganho é lançado de novo.
        Order order = orderWithCustomer(42L);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(officialCustomer()));
        when(systemConfigPort.getInt("cashback.carencia.dias", 7)).thenReturn(7);
        when(systemConfigPort.getInt("cashback.expiracao.dias", 180)).thenReturn(180);

        cashbackService.recordEarnedForOrder(order);
        cashbackService.recordEarnedForOrder(order);

        // orderWithCustomer() tem 1 item com taxa estampada -> 1 save por chamada; duas chamadas
        // para o MESMO pedido resultam em 2 saves, provando a duplicação do ganho.
        verify(cashbackEntryRepository, times(2)).save(any());
    }

    // ── Saldo e extrato ──────────────────────────────────────────────────────────────────────

    @Test
    void getCustomerBalance_throwsWhenCustomerDoesNotExist() {
        when(customerRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cashbackService.getCustomerBalance(1L))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    void getCustomerBalance_aggregatesAvailablePendingAndExpiringSoon() {
        when(customerRepository.findById(1L)).thenReturn(Optional.of(officialCustomer()));
        when(cashbackEntryRepository.sumAvailableByCustomerId(eq(1L), any())).thenReturn(new BigDecimal("30.00"));
        when(cashbackEntryRepository.sumPendingByCustomerId(eq(1L), any())).thenReturn(new BigDecimal("5.00"));
        when(cashbackEntryRepository.sumExpiringSoonByCustomerId(eq(1L), any(), any())).thenReturn(new BigDecimal("2.00"));

        var balance = cashbackService.getCustomerBalance(1L);

        assertThat(balance.available()).isEqualByComparingTo("30.00");
        assertThat(balance.pending()).isEqualByComparingTo("5.00");
        assertThat(balance.expiringSoon()).isEqualByComparingTo("2.00");
    }

    // ── Reversão por reembolso (PDV-F007) ───────────────────────────────────────────────────

    @Test
    void reverseEarningsForOrder_writesReversedEntryForEachEarnedEntryOfTheOrder() {
        Order order = orderWithCustomer(42L);
        CashbackEntry earnedA = CashbackEntry.of(5L, 42L, order.id(), 3L, CashbackEntryType.EARNED,
                new BigDecimal("10.00"), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"), null, Instant.parse("2026-01-01T00:00:00Z"));
        CashbackEntry earnedB = CashbackEntry.of(6L, 42L, order.id(), 4L, CashbackEntryType.EARNED,
                new BigDecimal("3.00"), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"), null, Instant.parse("2026-01-01T00:00:00Z"));
        when(cashbackEntryRepository.findEarnedByOrderId(order.id())).thenReturn(List.of(earnedA, earnedB));

        cashbackService.reverseEarningsForOrder(order);

        ArgumentCaptor<CashbackEntry> captor = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(entry -> assertThat(entry.type()).isEqualTo(CashbackEntryType.REVERSED));
        assertThat(captor.getAllValues())
                .extracting(CashbackEntry::reversesEntryId, CashbackEntry::amount)
                .containsExactlyInAnyOrder(
                        tuple(5L, new BigDecimal("-10.00")),
                        tuple(6L, new BigDecimal("-3.00")));
    }

    @Test
    void reverseEarningsForOrder_isANoOpWhenTheOrderNeverEarnedCashback() {
        Order order = orderWithCustomer(42L);
        when(cashbackEntryRepository.findEarnedByOrderId(order.id())).thenReturn(List.of());

        cashbackService.reverseEarningsForOrder(order);

        verify(cashbackEntryRepository, never()).save(any());
    }

    // ── Expiração ────────────────────────────────────────────────────────────────────────────

    @Test
    void expireEntries_writesAnExpiredEntryPerPendingEarned() {
        CashbackEntry earned = CashbackEntry.of(5L, 1L, 2L, 3L, CashbackEntryType.EARNED, new BigDecimal("10.00"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-07-01T00:00:00Z"), null,
                Instant.parse("2026-01-01T00:00:00Z"));
        when(cashbackEntryRepository.findEarnedPendingExpiry(any(), eq(50))).thenReturn(List.of(earned));

        int expired = cashbackService.expireEntries(50);

        assertThat(expired).isEqualTo(1);
        ArgumentCaptor<CashbackEntry> captor = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(CashbackEntryType.EXPIRED);
        assertThat(captor.getValue().reversesEntryId()).isEqualTo(5L);
        assertThat(captor.getValue().amount()).isEqualByComparingTo("-10.00");
    }

    @Test
    void expireEntries_doesNothingWhenNoneArePending() {
        when(cashbackEntryRepository.findEarnedPendingExpiry(any(), eq(50))).thenReturn(List.of());

        assertThat(cashbackService.expireEntries(50)).isZero();
        verify(cashbackEntryRepository, never()).save(any());
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────

    // ── CRM-F010: cashback da parte marcada ─────────────────────────────────────────────────

    @Test
    void recordEarnedForOrder_creditsOnlyThePaidFractionWhenPartIsOnAccount() {
        Order order = orderWithCustomer(42L); // 100,00 com 10% → 10,00 de cashback
        when(customerRepository.findById(42L)).thenReturn(Optional.of(officialCustomer()));
        when(systemConfigPort.getInt("cashback.carencia.dias", 7)).thenReturn(7);
        when(systemConfigPort.getInt("cashback.expiracao.dias", 180)).thenReturn(180);
        when(orderPaymentRepository.findByOrderId(1L)).thenReturn(List.of(
                com.cernecommerce.core.domain.model.pagamento.OrderPayment.captured(1L,
                        com.cernecommerce.core.domain.model.pagamento.PaymentMethod.PIX, new BigDecimal("30.00"), null),
                com.cernecommerce.core.domain.model.pagamento.OrderPayment.onAccount(1L, new BigDecimal("70.00"),
                        java.time.LocalDate.of(2026, 10, 15))));

        cashbackService.recordEarnedForOrder(order);

        ArgumentCaptor<CashbackEntry> captor = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("3.00");
    }

    @Test
    void recordEarnedForReceivablePayment_creditsTheSettledShareWithoutItem() {
        Order order = orderWithCustomer(42L);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(officialCustomer()));
        when(systemConfigPort.getInt("cashback.carencia.dias", 7)).thenReturn(7);
        when(systemConfigPort.getInt("cashback.expiracao.dias", 180)).thenReturn(180);

        cashbackService.recordEarnedForReceivablePayment(order, new BigDecimal("35.00"));

        ArgumentCaptor<CashbackEntry> captor = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository).save(captor.capture());
        assertThat(captor.getValue().amount()).isEqualByComparingTo("3.50");
        assertThat(captor.getValue().orderId()).isEqualTo(1L);
        assertThat(captor.getValue().orderItemId()).isNull();
    }

    @Test
    void recordEarnedForReceivablePayment_skipsCustomerWithoutCpf() {
        when(customerRepository.findById(42L)).thenReturn(Optional.of(lightCustomer()));

        cashbackService.recordEarnedForReceivablePayment(orderWithCustomer(42L), new BigDecimal("35.00"));

        verifyNoInteractions(cashbackEntryRepository);
    }

    private static Customer officialCustomer() {
        return Customer.of(42L, "Cliente Oficial", "11999999999", null, "12345678900", "balcao",
                Instant.now(), CustomerStage.NOVO_LEAD);
    }

    private static Customer lightCustomer() {
        return Customer.of(42L, "Cliente Leve", "11999999999", null, null, "balcao",
                Instant.now(), CustomerStage.NOVO_LEAD);
    }

    private static Order orderWithCustomer(Long customerId) {
        OrderItem item = OrderItem.of(9L, "CARV-001", BigDecimal.ONE, new BigDecimal("100.00"),
                new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("10.0"));
        Instant paidAt = Instant.parse("2026-07-29T12:00:00Z");
        return Order.of(1L, "000000001", SalesChannel.BALCAO, OrderStatus.CONCLUIDO, customerId, 1L,
                "LOJA-01", List.of(item), new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00"), null, null, paidAt, paidAt, paidAt, null, null, 0L);
    }
}
