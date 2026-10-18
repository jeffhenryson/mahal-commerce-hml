package com.cernecommerce.core.service;

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
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
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
 * PED-F006 — troca de item de ponta a ponta contra banco real: a perna devolvida volta ao estoque, o
 * novo item sai, o pedido ganha a linha nova e só a diferença entra (ou sai) da gaveta de quem trocou
 * — que não é a do caixa que vendeu.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OrderExchangeIT {

    @Autowired OrderUseCase orderUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static List<PaymentCommand> cash(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    private void givenProduct(String sku, String price, String warehouseCode, String operator) {
        estoqueUseCase.createProduct(sku, "Produto " + sku, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal(price)));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);
    }

    @Test
    void troca_comDiferencaAPagar_contaNoCaixaDeQuemTrocou() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String vendedor = "caixa-" + suffix;
        String trocador = "troca-" + suffix;
        String warehouseCode = "LOJA-" + suffix;
        String uva = "UVA-" + suffix;
        String menta = "MENTA-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        givenProduct(uva, "100.00", warehouseCode, vendedor);
        givenProduct(menta, "130.00", warehouseCode, vendedor);

        CashRegisterSession venda = pdvUseCase.openSession(vendedor, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(venda.id(), null,
                List.of(new SaleItemCommand(uva, new BigDecimal("2"), null)), cash("200.00"), vendedor);
        pdvUseCase.closeSession(venda.id(), new BigDecimal("300.00"), vendedor);
        flushAndClear();

        CashRegisterSession troca = pdvUseCase.openSession(trocador, new BigDecimal("50.00"), warehouseCode);
        OrderUseCase.ExchangeResult result = orderUseCase.exchangeItem(order.id(), new OrderUseCase.ExchangeCommand(
                uva, BigDecimal.ONE, RefundDestination.RESTOCK, menta, BigDecimal.ONE, "sabor errado",
                PaymentMethod.DINHEIRO), trocador);
        flushAndClear();

        // O pedido mostra o restante do original e o item novo, e segue CONCLUIDO.
        Order trocado = orderUseCase.getOrder(order.id());
        assertThat(trocado.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(trocado.items()).extracting(OrderItem::sku).containsExactly(uva, menta);
        assertThat(result.exchange().difference()).isEqualByComparingTo("30.00");
        Long uvaItemId = trocado.items().getFirst().id();
        assertThat(orderUseCase.getRefundedQuantities(order.id()).get(uvaItemId)).isEqualByComparingTo("1");

        // Estoque: 10 − 2 + 1 de uva; 10 − 1 de menta.
        assertThat(estoqueUseCase.getStockBalance(uva, warehouseCode).quantity()).isEqualByComparingTo("9.000");
        assertThat(estoqueUseCase.getStockBalance(menta, warehouseCode).quantity()).isEqualByComparingTo("9.000");

        // Só a diferença virou pagamento, CAPTURED no caixa de quem trocou.
        assertThat(pdvUseCase.getOrderPayments(order.id()))
                .filteredOn(p -> p.status() == PaymentStatus.CAPTURED)
                .extracting(OrderPayment::amount)
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("200.00"), new BigDecimal("30.00"));
        assertThat(pdvUseCase.getSessionPaymentTotals(troca.id()))
                .filteredOn(t -> t.method() == PaymentMethod.DINHEIRO)
                .singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualByComparingTo("30.00"));
        // O caixa que vendeu, já fechado, não muda.
        assertThat(pdvUseCase.getSessionPaymentTotals(venda.id()))
                .filteredOn(t -> t.method() == PaymentMethod.DINHEIRO)
                .singleElement()
                .satisfies(t -> assertThat(t.amount()).isEqualByComparingTo("200.00"));

        // A gaveta de quem trocou fecha certa: fundo 50 + diferença 30.
        CashRegisterSession fechado = pdvUseCase.closeSession(troca.id(), new BigDecimal("80.00"), trocador);
        assertThat(fechado.diverges()).isFalse();
    }

    @Test
    void troca_comDiferencaAReceberEmDinheiro_saiDaGaveta() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String operador = "caixa-" + suffix;
        String warehouseCode = "LOJA-" + suffix;
        String uva = "UVA-" + suffix;
        String seda = "SEDA-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        givenProduct(uva, "100.00", warehouseCode, operador);
        givenProduct(seda, "60.00", warehouseCode, operador);

        CashRegisterSession caixa = pdvUseCase.openSession(operador, new BigDecimal("100.00"), warehouseCode);
        Order order = pdvUseCase.registerSale(caixa.id(), null,
                List.of(new SaleItemCommand(uva, BigDecimal.ONE, null)), cash("100.00"), operador);
        flushAndClear();

        orderUseCase.exchangeItem(order.id(), new OrderUseCase.ExchangeCommand(uva, BigDecimal.ONE,
                RefundDestination.DISCARD, seda, BigDecimal.ONE, "defeito", PaymentMethod.DINHEIRO), operador);
        flushAndClear();

        // DISCARD: a uva devolvida não volta ao saldo.
        assertThat(estoqueUseCase.getStockBalance(uva, warehouseCode).quantity()).isEqualByComparingTo("9.000");
        assertThat(pdvUseCase.getOrderPayments(order.id()))
                .filteredOn(p -> p.status() == PaymentStatus.REFUNDED)
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.amount()).isEqualByComparingTo("40.00");
                    assertThat(p.refundSessionId()).isEqualTo(caixa.id());
                });

        // Fundo 100 + venda 100 − 40 devolvidos.
        CashRegisterSession fechado = pdvUseCase.closeSession(caixa.id(), new BigDecimal("160.00"), operador);
        assertThat(fechado.diverges()).isFalse();
    }
}
