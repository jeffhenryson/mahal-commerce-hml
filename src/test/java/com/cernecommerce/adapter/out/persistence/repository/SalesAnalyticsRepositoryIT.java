package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import com.cernecommerce.adapter.out.persistence.entity.StockBalanceEntity;
import com.cernecommerce.adapter.out.persistence.entity.WarehouseEntity;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesAnalytics;
import com.cernecommerce.core.domain.model.pedido.SalesGoal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pendência 9 contra banco (perfil dev, H2): o JPQL das análises tem que rodar fora do Postgres também.
 * O fuso é provado com uma venda às 22h de São Paulo (01h UTC do dia seguinte).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SalesAnalyticsRepositoryIT {

    @Autowired OrderRepositoryImpl orderRepository;
    @Autowired OrderPaymentRepositoryImpl orderPaymentRepository;
    @Autowired SalesAnalyticsRepositoryImpl analytics;
    @Autowired OrderReportRepositoryImpl orderReportRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired WarehouseJpaRepository warehouseJpaRepository;
    @Autowired StockBalanceJpaRepository stockBalanceJpaRepository;
    @PersistenceContext EntityManager em;

    private static final Pricing PRECO = Pricing.of(new BigDecimal("5.00"), null, new BigDecimal("10.00"));

    private Order vendaBalcao(String wh, String sku, String qty, Instant soldAt) {
        Order saved = orderRepository.save(Order.openBalcao(1L, wh, null,
                        List.of(OrderItem.fromCatalog(sku, new BigDecimal(qty), PRECO, null)))
                .concluded(orderRepository.nextOrderNumber(), null, Instant.now()));
        em.flush();
        em.createQuery("UPDATE OrderEntity o SET o.createdAt = :t WHERE o.id = :id")
                .setParameter("t", soldAt).setParameter("id", saved.id()).executeUpdate();
        return saved;
    }

    private void produto(String sku, String nome) {
        ProductEntity p = new ProductEntity();
        p.setSku(sku);
        p.setName(nome);
        p.setType("SIMPLES");
        p.setUnit("UN");
        p.setStatus("ATIVO");
        p.setActive(true);
        p.setCostPrice(new BigDecimal("5.00"));
        productJpaRepository.save(p);
    }

    private String deposito() {
        WarehouseEntity w = new WarehouseEntity();
        w.setCode("AN-" + UUID.randomUUID().toString().substring(0, 6));
        w.setName("Análise");
        w.setType(WarehouseType.LOJA_FISICA);
        w.setActive(true);
        return warehouseJpaRepository.save(w).getCode();
    }

    private void saldo(String wh, String sku, String qty, String avgCost) {
        StockBalanceEntity b = new StockBalanceEntity();
        b.setSku(sku);
        b.setWarehouseId(warehouseJpaRepository.findByCode(wh).orElseThrow().getId());
        b.setQuantity(new BigDecimal(qty));
        b.setReservedQuantity(BigDecimal.ZERO);
        b.setAverageCost(avgCost == null ? null : new BigDecimal(avgCost));
        stockBalanceJpaRepository.save(b);
    }

    @Test
    void pagamentosComTrocoESomaIgualAoLiquido() {
        String wh = deposito();
        Instant t = Instant.parse("2026-10-16T18:00:00Z");
        // Pedido de 20,00: Pix 5,00 + dinheiro 20,00 entregue, troco 5,00 → dinheiro conta 15,00.
        Order o = orderRepository.save(Order.openBalcao(1L, wh, null,
                        List.of(OrderItem.fromCatalog("AN-SKU", new BigDecimal("2"), PRECO, null)))
                .concluded(orderRepository.nextOrderNumber(), new BigDecimal("5.00"), Instant.now()));
        em.flush();
        em.createQuery("UPDATE OrderEntity o SET o.createdAt = :t WHERE o.id = :id")
                .setParameter("t", t).setParameter("id", o.id()).executeUpdate();
        orderPaymentRepository.save(OrderPayment.captured(o.id(), PaymentMethod.PIX, new BigDecimal("5.00"), null));
        orderPaymentRepository.save(OrderPayment.captured(o.id(), PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null));
        em.flush();

        List<SalesAnalytics.PaymentMethodTotal> totals = analytics.paymentTotals(null, wh,
                t.minusSeconds(60), t.plusSeconds(60));

        assertThat(totals).extracting(SalesAnalytics.PaymentMethodTotal::method).containsExactlyInAnyOrder("PIX",
                "DINHEIRO");
        BigDecimal soma = totals.stream().map(SalesAnalytics.PaymentMethodTotal::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(soma).isEqualByComparingTo("20.00");
        assertThat(totals).filteredOn(m -> m.method().equals("DINHEIRO"))
                .singleElement().satisfies(m -> assertThat(m.amount()).isEqualByComparingTo("15.00"));
    }

    @Test
    void summaryAgrupaNoDiaLocalEFiltraPorDeposito() {
        String wh = deposito();
        // 22h de São Paulo do dia 16 (01h UTC do dia 17) e 2h de São Paulo do dia 17 (05h UTC).
        vendaBalcao(wh, "AN-SKU", "1", Instant.parse("2026-10-17T01:00:00Z"));
        vendaBalcao(wh, "AN-SKU", "1", Instant.parse("2026-10-17T05:00:00Z"));
        vendaBalcao("OUTRO-DEP", "AN-SKU", "1", Instant.parse("2026-10-17T05:00:00Z"));
        em.flush();
        em.clear();

        OrderSummary summary = orderReportRepository.summarize(null, null, null, wh,
                Instant.parse("2026-10-16T03:00:00Z"), Instant.parse("2026-10-18T02:59:59Z"), 10);

        assertThat(summary.totalRevenueNet()).isEqualByComparingTo("20.00");
        assertThat(summary.dailyRevenue()).extracting(OrderSummary.DailyRevenue::date)
                .containsExactly(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 17));
    }

    @Test
    void pedidosPagosEOperadores() {
        String wh = deposito();
        Instant t = Instant.parse("2026-10-20T15:00:00Z");
        vendaBalcao(wh, "AN-SKU", "2", t);
        em.flush();

        List<SalesAnalytics.PaidOrder> orders = analytics.paidOrders("BALCAO", wh, t.minusSeconds(60),
                t.plusSeconds(60));
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).netAmount()).isEqualByComparingTo("20.00");
        assertThat(orders.get(0).sessionId()).isEqualTo(1L);
        assertThat(analytics.paidOrders("MARKETPLACE", wh, t.minusSeconds(60), t.plusSeconds(60))).isEmpty();
        // Sem sessão/usuário cadastrados, as consultas em lote só não acham nada.
        assertThat(analytics.operatorUserIds(List.of("ninguem-" + UUID.randomUUID()))).isEmpty();
    }

    @Test
    void encalhadosPorValorParado() {
        String wh = deposito();
        String parado = "AN-PAR-" + UUID.randomUUID().toString().substring(0, 4);
        String vende = "AN-VEN-" + UUID.randomUUID().toString().substring(0, 4);
        String barato = "AN-BAR-" + UUID.randomUUID().toString().substring(0, 4);
        produto(parado, "Charuto Parado");
        produto(vende, "Seda que Vende");
        produto(barato, "Isqueiro Barato");
        saldo(wh, parado, "10", "50.00");
        saldo(wh, vende, "100", null);
        saldo(wh, barato, "3", "2.00");
        Instant t = Instant.parse("2026-10-20T15:00:00Z");
        vendaBalcao(wh, vende, "4", t);
        vendaBalcao(wh, parado, "1", Instant.parse("2026-06-01T15:00:00Z"));
        em.flush();

        List<SalesAnalytics.SlowMover> movers = analytics.slowMovers(wh, Instant.parse("2026-10-01T03:00:00Z"),
                Instant.parse("2026-11-01T02:59:59Z"), BigDecimal.ZERO, 20);

        assertThat(movers).extracting(SalesAnalytics.SlowMover::sku).containsExactly(parado, barato);
        assertThat(movers.get(0).stockValue()).isEqualByComparingTo("500.00");
        assertThat(movers.get(0).lastSoldAt()).isEqualTo(Instant.parse("2026-06-01T15:00:00Z"));
        assertThat(movers.get(1).lastSoldAt()).isNull();
    }

    @Test
    void summarySeparaOFreteDoLiquido() {
        String wh = deposito();
        Instant t = Instant.parse("2026-10-16T18:00:00Z");
        Order o = orderRepository.save(Order.openBalcao(1L, wh, null,
                        List.of(OrderItem.fromCatalog("AN-SKU", new BigDecimal("2"), PRECO, null)),
                        new OrderDelivery(DeliveryType.ENTREGA,
                                new DeliveryAddress("Rua A", "10", null, null, null, "Botucatu", "SP", null, null),
                                DeliveryMethod.APP_99, null, null, null, null, null, new BigDecimal("8.00")))
                .reserved(orderRepository.nextOrderNumber(), null, Instant.now()));
        em.flush();
        em.createQuery("UPDATE OrderEntity o SET o.createdAt = :t WHERE o.id = :id")
                .setParameter("t", t).setParameter("id", o.id()).executeUpdate();
        em.clear();

        OrderSummary summary = orderReportRepository.summarize(null, null, null, wh, t.minusSeconds(60),
                t.plusSeconds(60), 10);

        assertThat(summary.totalRevenueNet()).isEqualByComparingTo("20.00");
        assertThat(summary.deliveryFeeTotal()).isEqualByComparingTo("8.00");
        assertThat(summary.serviceFeeTotal()).isEqualByComparingTo("0");
    }

    @Test
    void metaApagadaSomeEApagarDeNovoAvisa() {
        YearMonth mes = YearMonth.of(2031, 3);
        analytics.saveGoal(new SalesGoal(mes, new BigDecimal("1000.00"), "dono", Instant.now()));
        em.flush();

        assertThat(analytics.deleteGoal(mes)).isTrue();
        em.flush();

        assertThat(analytics.findGoal(mes)).isEmpty();
        assertThat(analytics.deleteGoal(mes)).isFalse();
    }

    @Test
    void metaGravaESubstitui() {
        YearMonth mes = YearMonth.of(2031, 1);
        analytics.saveGoal(new SalesGoal(mes, new BigDecimal("1000.00"), "gerente", Instant.now()));
        analytics.saveGoal(new SalesGoal(mes, new BigDecimal("1500.00"), "dono", Instant.now()));
        em.flush();
        em.clear();

        SalesGoal goal = analytics.findGoal(mes).orElseThrow();
        assertThat(goal.amount()).isEqualByComparingTo("1500.00");
        assertThat(goal.updatedBy()).isEqualTo("dono");
        assertThat(analytics.findGoal(YearMonth.of(2031, 2))).isEmpty();
    }
}
