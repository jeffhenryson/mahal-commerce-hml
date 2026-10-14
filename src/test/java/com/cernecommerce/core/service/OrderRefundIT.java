package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackEntryType;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.RefundDestination;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cancelamento e reembolso de pedido (Fatia 5, PDV-F007), de ponta a ponta contra banco real.
 *
 * <p>Dois caminhos, nunca o mesmo: {@code cancelOrder} para pedido pré-pagamento (nada a estornar)
 * e {@code refundOrder} para pedido pós-pagamento (estoque, pagamento e cashback revertidos juntos).
 * Ver a divisão em {@code OrderStatus} e a orquestração em {@code OrderService}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OrderRefundIT {

    @Autowired OrderUseCase orderUseCase;
    @Autowired OrderRepository orderRepository;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired CashbackUseCase cashbackUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** cpf é VARCHAR(11) — precisa de um valor numérico, não do sufixo alfanumérico usado alhures. */
    private String uniqueCpf() {
        return String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
    }

    private static List<PaymentCommand> cash(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private String givenStockedWarehouse(String operator, String suffix) {
        String warehouseCode = "LOJA-" + suffix;
        String sku = "ESSENCIA-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);
        return warehouseCode + "|" + sku;
    }

    @Test
    void refundOrder_reversesStockPaymentAndCashbackForAConcludedBalcaoSale() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator, suffix).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        Customer customer = crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                uniqueCpf(), "balcao");
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(session.id(), customer.id(),
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("100.00"), operator);
        flushAndClear();

        // Antes do reembolso: estoque baixado, um pagamento CAPTURED, um EARNED em carência.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("9.000");

        Order refunded = orderUseCase.refundOrder(order.id(), "devolução no prazo", operator);
        flushAndClear();

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        assertThat(refunded.refundedAt()).isNotNull();
        assertThat(refunded.cancelReason()).isEqualTo("devolução no prazo");

        // Estoque (EST-F014): a mercadoria volta à prateleira.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("10.000");

        // Pagamento: a linha CAPTURED original permanece, mais uma REFUNDED nova do mesmo valor.
        List<OrderPayment> payments = pdvUseCase.getOrderPayments(order.id());
        assertThat(payments).hasSize(2);
        assertThat(payments).anySatisfy(p -> {
            assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
            assertThat(p.amount()).isEqualByComparingTo("100.00");
            assertThat(p.method()).isEqualTo(PaymentMethod.DINHEIRO);
        });
        assertThat(payments).anySatisfy(p -> {
            assertThat(p.status()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(p.amount()).isEqualByComparingTo("100.00");
            assertThat(p.method()).isEqualTo(PaymentMethod.DINHEIRO);
        });

        // Cashback: o EARNED original continua no extrato, mais um REVERSED referenciando-o.
        List<CashbackEntry> entries = cashbackUseCase.listCustomerEntries(customer.id(), 0, 10).content();
        assertThat(entries).hasSize(2);
        CashbackEntry earned = entries.stream().filter(e -> e.type() == CashbackEntryType.EARNED)
                .findFirst().orElseThrow();
        assertThat(earned.amount()).isEqualByComparingTo("3.00");
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.type()).isEqualTo(CashbackEntryType.REVERSED);
            assertThat(e.amount()).isEqualByComparingTo("-3.00");
            assertThat(e.reversesEntryId()).isEqualTo(earned.id());
        });

        // O ganho revertido não conta mais como pendente.
        CashbackBalance balance = cashbackUseCase.getCustomerBalance(customer.id());
        assertThat(balance.pending()).isEqualByComparingTo("0.00");
    }

    /**
     * PED-F004 — estorno por item contra banco real: RESTOCK volta ao saldo, DISCARD não; o dinheiro
     * sai na sessão de quem estorna; o pedido só vira REEMBOLSADO quando a última unidade sai.
     */
    @Test
    void refundPorItem_parcialDepoisTotal() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator, suffix).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, new BigDecimal("3"), null)), cash("300.00"), operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity()).isEqualByComparingTo("7.000");

        Order partial = orderUseCase.refundOrder(order.id(), new OrderUseCase.RefundCommand("defeito", true, List.of(),
                List.of(new OrderUseCase.RefundItemCommand(sku, BigDecimal.ONE, RefundDestination.DISCARD),
                        new OrderUseCase.RefundItemCommand(sku, BigDecimal.ONE, RefundDestination.RESTOCK))),
                operator, false);
        flushAndClear();

        assertThat(partial.status()).isNotEqualTo(OrderStatus.REEMBOLSADO);
        // Só o RESTOCK voltou: 7 + 1. A unidade descartada já tinha saído na venda.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity()).isEqualByComparingTo("8.000");
        assertThat(orderUseCase.getRefundedQuantities(order.id()).values())
                .singleElement().satisfies(q -> assertThat(q).isEqualByComparingTo("2"));
        assertThat(pdvUseCase.getSessionPaymentTotals(session.id()))
                .filteredOn(t -> t.method() == PaymentMethod.DINHEIRO)
                .singleElement()
                .satisfies(t -> assertThat(t.refundedAmount()).isEqualByComparingTo("200.00"));

        Order refunded = orderUseCase.refundOrder(order.id(), "desistiu do resto", operator);
        flushAndClear();

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity()).isEqualByComparingTo("9.000");
        assertThat(pdvUseCase.getOrderPayments(order.id()))
                .filteredOn(p -> p.status() == PaymentStatus.REFUNDED)
                .extracting(OrderPayment::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("200.00"), new BigDecimal("100.00"));
    }

    @Test
    void cancelOrder_onAPrePaymentMarketplaceOrderTouchesNeitherPaymentNorCashback() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator, suffix).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        Customer customer = crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                uniqueCpf(), "balcao");
        flushAndClear();

        // Ainda não existe endpoint de checkout de marketplace (Fatia 8/9) — o fixture é
        // persistido diretamente, como o próprio OrderTest/OrderServiceTest já fazem.
        OrderItem item = OrderItem.fromCatalog(sku, BigDecimal.ONE, estoqueUseCase.findPricingBySku(sku), null);
        Order pending = orderRepository.save(Order.openMarketplace(customer.id(), warehouseCode, List.of(item)));
        flushAndClear();

        Order cancelled = orderUseCase.cancelOrder(pending.id(), "cliente desistiu antes de pagar", operator);
        flushAndClear();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELADO);
        assertThat(cancelled.cancelledAt()).isNotNull();
        assertThat(pdvUseCase.getOrderPayments(pending.id())).isEmpty();
        assertThat(cashbackUseCase.listCustomerEntries(customer.id(), 0, 10).content()).isEmpty();
    }
}
