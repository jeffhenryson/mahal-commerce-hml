package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.recebivel.CreditLimitExceededException;
import com.cernecommerce.core.domain.exception.recebivel.CustomerNotEligibleForOnAccountException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.ReceivableStatus;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.crm.TagRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Marcar" de ponta a ponta contra banco (CRM-F010): venda parcialmente marcada, MARCADO fora do
 * caixa, quitação no caixa de quem recebe (outro operador), troco e o esperado do fechamento.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ReceivableCycleIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired OrderUseCase orderUseCase;
    @Autowired ReceivableUseCase receivableUseCase;
    @Autowired TagRepository tagRepository;

    @PersistenceContext EntityManager em;

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueCpf() {
        return String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Depósito + carvão a 22,00 com saldo. Devolve [warehouse, sku]. */
    private String[] givenStock(String operator) {
        String s = suffix();
        estoqueUseCase.createWarehouse("LOJA-" + s, "Loja " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct("CARV-" + s, "Carvão " + s, "Carvões", List.of(),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")));
        estoqueUseCase.adjustStock("CARV-" + s, "LOJA-" + s, MovementType.ENTRADA, new BigDecimal("20.000"),
                "carga", operator);
        return new String[] {"LOJA-" + s, "CARV-" + s};
    }

    private Customer givenCustomer(boolean vip, String creditLimit) {
        Customer customer = crmUseCase.createCustomer("Cliente " + suffix(), null, null, uniqueCpf(), "PDV");
        if (vip) {
            Tag tag = tagRepository.findByNome("VIP").orElseGet(() -> tagRepository.save(new Tag(null, "VIP")));
            crmUseCase.addTagToCustomer(customer.id(), tag.id());
        }
        if (creditLimit != null) {
            receivableUseCase.setCreditLimit(customer.id(), new BigDecimal(creditLimit), "gerente");
        }
        return customer;
    }

    private static BigDecimal totalOf(List<PdvUseCase.PaymentTotal> totals, PaymentMethod method,
            java.util.function.Function<PdvUseCase.PaymentTotal, BigDecimal> field) {
        return totals.stream().filter(t -> t.method() == method).map(field).findFirst().orElseThrow();
    }

    @Test
    void partialOnAccountSale_thenSettledInCashAtAnotherOperatorsDrawer() {
        String ana = "ana-" + suffix();
        String bia = "bia-" + suffix();
        String[] stock = givenStock(ana);
        Customer vip = givenCustomer(true, "100.00");
        CashRegisterSession anaSession = pdvUseCase.openSession(ana, new BigDecimal("50.00"), stock[0]);
        CashRegisterSession biaSession = pdvUseCase.openSession(bia, new BigDecimal("0.00"), stock[0]);

        // 2 carvões = 44,00: 14,00 no PIX e 30,00 marcados.
        Order sold = pdvUseCase.registerSale(anaSession.id(), vip.id(),
                List.of(new SaleItemCommand(stock[1], new BigDecimal("2.000"), null)),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("14.00"), null),
                        PaymentCommand.onAccount(new BigDecimal("30.00"), TODAY.plusDays(10))), ana);
        flushAndClear();

        assertThat(sold.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(orderUseCase.getOrderPayments(sold.id()))
                .extracting(p -> p.method(), p -> p.status())
                .contains(org.assertj.core.groups.Tuple.tuple(PaymentMethod.MARCADO, PaymentStatus.ON_ACCOUNT));

        List<PdvUseCase.PaymentTotal> anaTotals = pdvUseCase.getSessionPaymentTotals(anaSession.id());
        assertThat(anaTotals).extracting(PdvUseCase.PaymentTotal::method).doesNotContain(PaymentMethod.MARCADO);
        assertThat(totalOf(anaTotals, PaymentMethod.PIX, PdvUseCase.PaymentTotal::netAmount))
                .isEqualByComparingTo("14.00");

        CustomerReceivable receivable = receivableUseCase.findByOrderId(sold.id()).orElseThrow();
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.ABERTO);
        assertThat(receivable.amount()).isEqualByComparingTo("30.00");
        assertThat(receivable.items()).singleElement()
                .satisfies(i -> assertThat(i.subtotal()).isEqualByComparingTo("44.00"));
        assertThat(receivableUseCase.eligibility(vip.id(), true).available()).isEqualByComparingTo("70.00");

        // Bia recebe 50 em dinheiro na gaveta DELA: abate 30, devolve 20 de troco.
        ReceivableUseCase.SettlementResult result = receivableUseCase.pay(biaSession.id(), bia, vip.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("50.00"), null)), null);
        flushAndClear();

        assertThat(result.changeAmount()).isEqualByComparingTo("20.00");
        assertThat(result.openBalanceAfter()).isEqualByComparingTo("0");
        assertThat(result.applied()).singleElement()
                .satisfies(a -> assertThat(a.statusAfter()).isEqualTo(ReceivableStatus.QUITADO));
        assertThat(receivableUseCase.get(receivable.id()).payments()).singleElement()
                .satisfies(p -> assertThat(p.cashSessionId()).isEqualTo(biaSession.id()));

        List<PdvUseCase.PaymentTotal> biaTotals = pdvUseCase.getSessionPaymentTotals(biaSession.id());
        assertThat(totalOf(biaTotals, PaymentMethod.DINHEIRO, PdvUseCase.PaymentTotal::receivableReceived))
                .isEqualByComparingTo("30.00");
        assertThat(totalOf(biaTotals, PaymentMethod.DINHEIRO, PdvUseCase.PaymentTotal::changeAmount))
                .isEqualByComparingTo("0.00");
        assertThat(totalOf(biaTotals, PaymentMethod.DINHEIRO, PdvUseCase.PaymentTotal::netAmount))
                .isEqualByComparingTo("30.00");
        // Nada da quitação vai para a gaveta da Ana.
        assertThat(totalOf(pdvUseCase.getSessionPaymentTotals(anaSession.id()), PaymentMethod.DINHEIRO,
                PdvUseCase.PaymentTotal::netAmount)).isEqualByComparingTo("0.00");

        CashRegisterSession closed = pdvUseCase.closeSession(biaSession.id(), new BigDecimal("30.00"), bia);
        assertThat(closed.expectedAmount()).isEqualByComparingTo("30.00");
        assertThat(closed.differenceAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void nonVipAndOverLimit_areRefusedBeforeTheSale() {
        String ana = "ana-" + suffix();
        String[] stock = givenStock(ana);
        CashRegisterSession session = pdvUseCase.openSession(ana, BigDecimal.ZERO, stock[0]);
        Customer common = givenCustomer(false, "500.00");
        Customer vip = givenCustomer(true, "20.00");

        assertThatThrownBy(() -> pdvUseCase.registerSale(session.id(), common.id(),
                List.of(new SaleItemCommand(stock[1], new BigDecimal("1.000"), null)),
                List.of(PaymentCommand.onAccount(new BigDecimal("22.00"), TODAY)), ana))
                .isInstanceOf(CustomerNotEligibleForOnAccountException.class);
        assertThatThrownBy(() -> pdvUseCase.registerSale(session.id(), vip.id(),
                List.of(new SaleItemCommand(stock[1], new BigDecimal("1.000"), null)),
                List.of(PaymentCommand.onAccount(new BigDecimal("22.00"), TODAY)), ana))
                .isInstanceOf(CreditLimitExceededException.class);
    }

    @Test
    void refundingTheOrder_cancelsTheOpenReceivable() {
        String ana = "ana-" + suffix();
        String[] stock = givenStock(ana);
        Customer vip = givenCustomer(true, "100.00");
        CashRegisterSession session = pdvUseCase.openSession(ana, BigDecimal.ZERO, stock[0]);
        Order sold = pdvUseCase.registerSale(session.id(), vip.id(),
                List.of(new SaleItemCommand(stock[1], new BigDecimal("1.000"), null)),
                List.of(PaymentCommand.onAccount(new BigDecimal("22.00"), TODAY.plusDays(5))), ana);
        flushAndClear();

        orderUseCase.refundOrder(sold.id(), "devolveu", "gerente");
        flushAndClear();

        CustomerReceivable receivable = receivableUseCase.findByOrderId(sold.id()).orElseThrow();
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.CANCELADO);
        assertThat(receivableUseCase.balance(vip.id()).openBalance()).isEqualByComparingTo("0");
    }
}
