package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.cashback.CashbackRedemptionNotAllowedException;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V186 — resgate de cashback no balcão, de ponta a ponta contra banco real: o resgate abate o
 * total, debita o saldo, o ganho da venda sai só sobre o que foi pago em dinheiro, o estorno
 * devolve o resgate e a expiração depois de um resgate parcial não deixa o saldo negativo.
 *
 * <p>Os parâmetros são ajustados no próprio teste (mínimo 0, teto 100%) para não precisar de
 * R$ 1.700 em compras até o saldo mínimo padrão de R$ 50. A gravação é desfeita pelo rollback; o
 * cache do {@code system_config} é limpo depois da transação, para não vazar para outro teste.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CashbackRedemptionFlowIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired CashbackUseCase cashbackUseCase;
    @Autowired OrderUseCase orderUseCase;
    @Autowired ObjectProvider<CacheManager> cacheManager;

    @PersistenceContext EntityManager em;

    @AfterTransaction
    void clearConfigCache() {
        CacheManager manager = cacheManager.getIfAvailable();
        if (manager != null) {
            manager.getCacheNames().forEach(name -> {
                var cache = manager.getCache(name);
                if (cache != null) {
                    cache.clear();
                }
            });
        }
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueCpf() {
        return String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
    }

    private static List<PaymentCommand> cash(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    /** Loja, produto de R$ 100 (3% GLOBAL da V70), cliente com CPF, caixa aberto e R$ 15 de cashback já liberado. */
    private record Scenario(String operator, String sku, Customer customer, CashRegisterSession session) {
    }

    private Scenario givenCustomerWithFifteenOfCashback() {
        cashbackUseCase.updateSettings(new CashbackSettings(7, 180, new BigDecimal("100"), BigDecimal.ZERO), "teste");
        String s = suffix();
        String operator = "caixa-" + s;
        String warehouse = "LOJA-" + s;
        String sku = "ESSENCIA-" + s;
        estoqueUseCase.createWarehouse(warehouse, "Loja " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + s, "Essências", List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("20.000"), "carga", operator);
        Customer customer = crmUseCase.createCustomer("Cliente " + s, "1199" + s, null, uniqueCpf(), "balcao");
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouse);
        // 5 x 100 = 500 → 3% = 15,00 em carência; liberado na hora (desbloqueio antecipado).
        pdvUseCase.registerSale(session.id(), customer.id(),
                List.of(new SaleItemCommand(sku, new BigDecimal("5"), null)), cash("500.00"), operator);
        cashbackUseCase.releasePending(customer.id());
        flushAndClear();
        assertThat(cashbackUseCase.getCustomerBalance(customer.id()).available()).isEqualByComparingTo("15.00");
        return new Scenario(operator, sku, customer, session);
    }

    private Order sellOneRedeeming(Scenario sc, String redeem, String paid) {
        return pdvUseCase.registerSaleIdempotent(sc.session().id(), sc.customer().id(),
                List.of(new SaleItemCommand(sc.sku(), BigDecimal.ONE, null)), List.of(), cash(paid), sc.operator(),
                false, null, null, null, PdvUseCase.PromotionCheck.strict(null), new BigDecimal(redeem)).order();
    }

    @Test
    void redeemAtTheCounter_reducesTheBillDebitsTheBalanceAndEarnsOnlyOnCashPaid() {
        Scenario sc = givenCustomerWithFifteenOfCashback();

        Order order = sellOneRedeeming(sc, "10.00", "90.00");
        flushAndClear();

        assertThat(order.cashbackRedeemed()).isEqualByComparingTo("10.00");
        assertThat(order.netAmount()).isEqualByComparingTo("90.00");
        CashbackBalance balance = cashbackUseCase.getCustomerBalance(sc.customer().id());
        assertThat(balance.available()).isEqualByComparingTo("5.00");
        // 3% de 90 (não de 100): a parte paga com cashback não gera cashback.
        assertThat(balance.pending()).isEqualByComparingTo("2.70");

        List<CashbackEntry> entries = cashbackUseCase.listCustomerEntries(sc.customer().id(), 0, 20).content();
        assertThat(entries).filteredOn(e -> e.type() == CashbackEntryType.REDEEMED).singleElement()
                .satisfies(e -> {
                    assertThat(e.orderId()).isEqualTo(order.id());
                    assertThat(e.amount()).isEqualByComparingTo("-10.00");
                });
    }

    @Test
    void redeemAboveBalance_isRefused() {
        Scenario sc = givenCustomerWithFifteenOfCashback();

        assertThatThrownBy(() -> sellOneRedeeming(sc, "15.01", "84.99"))
                .isInstanceOf(CashbackRedemptionNotAllowedException.class)
                .extracting("code").isEqualTo(CashbackRedemptionNotAllowedException.INSUFFICIENT_BALANCE);
    }

    @Test
    void refund_givesTheRedeemedCashbackBackAndReversesTheNewEarning() {
        Scenario sc = givenCustomerWithFifteenOfCashback();
        Order order = sellOneRedeeming(sc, "10.00", "90.00");
        flushAndClear();

        orderUseCase.refundOrder(order.id(), "desistiu", sc.operator());
        flushAndClear();

        CashbackBalance balance = cashbackUseCase.getCustomerBalance(sc.customer().id());
        assertThat(balance.available()).isEqualByComparingTo("15.00");
        assertThat(balance.pending()).isEqualByComparingTo("0.00");
        assertThat(cashbackUseCase.listCustomerEntries(sc.customer().id(), 0, 20).content())
                .filteredOn(e -> e.type() == CashbackEntryType.REDEMPTION_REVERSED).singleElement()
                .satisfies(e -> assertThat(e.amount()).isEqualByComparingTo("10.00"));
    }

    @Test
    void expiryAfterPartialRedemption_expiresOnlyTheRemainder() {
        Scenario sc = givenCustomerWithFifteenOfCashback();
        sellOneRedeeming(sc, "10.00", "90.00");
        flushAndClear();

        // Vence o ganho original de R$ 15 (o da segunda venda continua em carência).
        em.createNativeQuery("UPDATE cashback_entry SET available_at = CURRENT_TIMESTAMP - INTERVAL '2' DAY, "
                        + "expires_at = CURRENT_TIMESTAMP - INTERVAL '1' DAY "
                        + "WHERE customer_id = :c AND type = 'EARNED' AND amount = 15.00")
                .setParameter("c", sc.customer().id())
                .executeUpdate();
        flushAndClear();

        cashbackUseCase.expireEntries(50);
        flushAndClear();

        CashbackBalance balance = cashbackUseCase.getCustomerBalance(sc.customer().id());
        assertThat(balance.available()).isEqualByComparingTo("0.00");
        assertThat(cashbackUseCase.listCustomerEntries(sc.customer().id(), 0, 20).content())
                .filteredOn(e -> e.type() == CashbackEntryType.EXPIRED).singleElement()
                .satisfies(e -> assertThat(e.amount()).isEqualByComparingTo("-5.00"));
    }
}
