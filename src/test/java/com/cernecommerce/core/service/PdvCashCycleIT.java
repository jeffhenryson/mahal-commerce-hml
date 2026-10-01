package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.ports.in.CrmUseCase;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ciclo de caixa de ponta a ponta contra banco real (PDV-F001/F002/C004).
 *
 * <p>É o teste que <b>prova a fatia</b>: abre o caixa, vende, faz sangria, fecha com valor contado
 * divergente e confere que a sessão fechou <b>apesar</b> da divergência. Os testes de unidade
 * mockam os repositórios e passariam mesmo com o mapeamento das colunas novas da V66 errado.</p>
 *
 * <p><b>Limitação conhecida:</b> o índice parcial único que garante "uma sessão aberta por operador"
 * ({@code uk_cash_register_session_open_operator}, V66) <b>não existe</b> nestes testes. O perfil
 * {@code dev} monta o schema por {@code ddl-auto} a partir das entities, e o H2 não suporta
 * {@code CREATE UNIQUE INDEX ... WHERE}. O que se prova aqui é a checagem de domínio; a garantia sob
 * concorrência só existe em Postgres e continua não exercitada por teste algum.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PdvCashCycleIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    /** PDV-C015 — para montar o pedido do app como o checkout o deixa. */
    @Autowired com.cernecommerce.core.ports.out.pedido.OrderRepository orderRepository;
    /** PDV-C018 — o estorno é o único caminho que tira dinheiro da gaveta por fora da sangria. */
    @Autowired OrderUseCase orderUseCase;
    /** PDV-C015 — pedido de MARKETPLACE exige cliente de verdade: o cashback vai buscá-lo no CRM. */
    @Autowired CrmUseCase crmUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Nome único por teste: o índice de depósito é global e os testes compartilham o schema. */
    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** CPF único por teste — o cadastro de cliente é global e os testes compartilham o schema. */
    private String uniqueCpf() {
        return String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
    }

    /** Uma linha de pagamento em dinheiro, exata. */
    private static List<PaymentCommand> cash(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(amount), null));
    }

    /**
     * Depósito + produto precificado + saldo inicial. Cadastrar o SKU é pré-condição desde EST-C002:
     * movimentar SKU inexistente é 404.
     */
    private String givenStockedWarehouse(String operator) {
        String suffix = uniqueSuffix();
        String warehouseCode = "LOJA-" + suffix;
        String sku = "CARV-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        // Carvão do exemplo do plano: custo 18,00, venda 22,00.
        estoqueUseCase.createProduct(sku, "Carvão " + suffix, "Carvões", List.of(),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", operator);
        return warehouseCode + "|" + sku;
    }

    @Test
    void fullCycle_openSellWithdrawAndCloseWithDivergence() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        // 1. Abre o caixa com 200,00 de fundo de troco.
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("200.00"), warehouseCode);
        assertThat(session.id()).isNotNull();
        assertThat(session.isOpen()).isTrue();
        flushAndClear();

        // 2. Vende 2 unidades. O preço vem do catálogo; o depósito, da sessão.
        Order order = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, new BigDecimal("2.000"), null)), cash("44.00"), operator);
        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.netAmount()).isEqualByComparingTo("44.00");
        assertThat(order.warehouseCode()).isEqualTo(warehouseCode);
        assertThat(order.orderNumber()).isNotBlank();
        flushAndClear();

        // A venda saiu do estoque de verdade: 50 − 2.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");

        // 3. Sangria de 150,00.
        pdvUseCase.registerCashMovement(session.id(), CashMovementType.SANGRIA, new BigDecimal("150.00"),
                "depósito no cofre", operator);
        flushAndClear();

        // 4. Fecha contando 90,00 — esperado 200 + 44 − 150 = 94,00, faltam 4,00.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("90.00"), "gerente");

        assertThat(closed.expectedAmount()).isEqualByComparingTo("94.00");
        assertThat(closed.countedAmount()).isEqualByComparingTo("90.00");
        assertThat(closed.differenceAmount()).isEqualByComparingTo("-4.00");
        assertThat(closed.diverges()).isTrue();
        // O ponto da fatia: divergência é registrada, não bloqueia.
        assertThat(closed.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
        assertThat(closed.closedBy()).isEqualTo("gerente");
    }

    /**
     * PDV-F006 de ponta a ponta: pagamento dividido, troco só do dinheiro, o fechamento ignorando
     * o débito e os totais por forma de pagamento batendo. É o único teste que exercita a query
     * nativa de {@code sumCapturedAmountBySessionIdAndMethod} contra banco real.
     */
    @Test
    void splitPaymentIsPersistedAndOnlyCashCountsTowardsTheDrawer() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("50.00"), warehouseCode);

        // Pedido de 22,00: R$15 no débito + R$10 em dinheiro — troco de R$3, só do dinheiro.
        List<PaymentCommand> split = List.of(
                new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("15.00"), null),
                new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("10.00"), null));
        Order order = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), split, operator);
        flushAndClear();

        assertThat(order.changeAmount()).isEqualByComparingTo("3.00");
        assertThat(pdvUseCase.getOrderPayments(order.id())).hasSize(2);

        List<PdvUseCase.PaymentTotal> totals = pdvUseCase.getSessionPaymentTotals(session.id());
        assertThat(totals).hasSize(4);
        assertThat(totalFor(totals, PaymentMethod.DEBITO)).isEqualByComparingTo("15.00");
        assertThat(totalFor(totals, PaymentMethod.PIX)).isEqualByComparingTo("0");

        // Esperado na gaveta: 50 (abertura) + 10 (dinheiro entregue) − 3 (troco devolvido) = 57.
        //
        // Os 15 do débito ficam de fora: vão para conferência contra a adquirente, não contra o
        // contado aqui (PDV-F006). E o troco SAI (PDV-C017): a linha de pagamento guarda o valor
        // que o cliente entregou, não o que ficou na gaveta — até PDV-C017 este teste afirmava 60,
        // um valor que a gaveta nunca poderia conter, e com ele todo turno com venda quebrada em
        // dinheiro fechava acusando falta.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("57.00"), operator);
        assertThat(closed.expectedAmount()).isEqualByComparingTo("57.00");
        assertThat(closed.diverges()).isFalse();
    }

    /**
     * PDV-C015 — o pedido do app pago no balcão entra na conferência da gaveta.
     *
     * <p>Era o único caminho de recebimento do projeto que não gravava linha de pagamento: o
     * cliente montava o pedido no aplicativo, vinha pagar em dinheiro na loja, e
     * {@code closeSession} — que soma {@code order_payment}, não pedido — não esperava aquela
     * cédula. O turno fechava acusando <b>sobra</b> sem dono, e o valor não aparecia em
     * {@code /payment-totals} nem no comprovante.</p>
     */
    @Test
    void settledOnlineOrderPaidInCashCountsTowardsTheExpectedAmount() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);

        // Cliente de verdade: pedido de MARKETPLACE exige customerId, e o recordEarnedForOrder do
        // fim da liquidação vai buscá-lo no CRM — um id inventado falha com CustomerNotFound.
        Customer cliente = crmUseCase.createCustomer("Cliente " + uniqueSuffix(),
                "1199" + uniqueSuffix(), null, uniqueCpf(), "app");

        // O pedido como o checkout do app o deixa: AGUARDANDO_PAGAMENTO, com o estoque já
        // RESERVADO — é por isso que liquidar consome a reserva em vez de debitar de novo.
        Order online = orderRepository.save(Order.of(null, null, SalesChannel.MARKETPLACE,
                OrderStatus.AGUARDANDO_PAGAMENTO, cliente.id(), null, warehouseCode,
                List.of(OrderItem.fromCatalog(sku, new BigDecimal("2.000"),
                        estoqueUseCase.resolveSaleInfo(sku).pricing(), null, "Carvão")),
                new BigDecimal("44.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("44.00"),
                null, null, Instant.now(), null, null, null, null, 0L));
        estoqueUseCase.reserveStock(sku, warehouseCode, new BigDecimal("2.000"),
                Order.reservationOwnerReference(online.id()), null, operator);
        flushAndClear();

        Order liquidado = pdvUseCase.settleOnlineOrder(session.id(), online.id(), cash("44.00"), operator);
        flushAndClear();

        // O canal continua sendo a origem da venda; o que mudou é a gaveta que responde por ela.
        assertThat(liquidado.channel()).isEqualTo(SalesChannel.MARKETPLACE);
        assertThat(liquidado.sessionId()).isEqualTo(session.id());

        // O que faltava: a linha de pagamento existe, e o detalhamento do fechamento a enxerga.
        assertThat(pdvUseCase.getOrderPayments(liquidado.id()))
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.method()).isEqualTo(PaymentMethod.DINHEIRO);
                    assertThat(p.amount()).isEqualByComparingTo("44.00");
                    assertThat(p.status()).isEqualTo(PaymentStatus.CAPTURED);
                });
        assertThat(totalFor(pdvUseCase.getSessionPaymentTotals(session.id()), PaymentMethod.DINHEIRO))
                .isEqualByComparingTo("44.00");

        // E a gaveta fecha certo: 100 (abertura) + 44 (a cédula do pedido do app) = 144.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("144.00"), operator);
        assertThat(closed.expectedAmount()).isEqualByComparingTo("144.00");
        assertThat(closed.diverges()).isFalse();
    }

    private BigDecimal totalFor(List<PdvUseCase.PaymentTotal> totals, PaymentMethod method) {
        return totals.stream().filter(t -> t.method() == method).findFirst().orElseThrow().amount();
    }

    /**
     * PDV-C018 — a cédula devolvida ao cliente sai do esperado.
     *
     * <p>Este teste se chamava {@code cancelledSaleDoesNotCountTowardsTheExpectedAmount} e
     * <b>não cancelava nada</b>: abria o caixa, registrava uma venda e fechava, com um comentário
     * interno dizendo "sem venda cancelada". A regra que o README dizia estar coberta por ele
     * ("venda cancelada não conta no expectedAmount") nunca foi exercitada — e não estava correta:
     * o ledger é append-only, o estorno grava uma linha {@code REFUNDED} nova e deixa a
     * {@code CAPTURED} de pé, então a soma de capturados continuava contando a cédula que voltou
     * para o cliente.</p>
     *
     * <p>Estorno e não cancelamento porque é o que a máquina de estados permite: venda de balcão
     * nasce {@code CONCLUIDO} com pagamento capturado, e estado pós-pagamento só alcança
     * {@code REEMBOLSADO} ({@code OrderStatus}). "Cancelar uma venda de balcão" não existe.</p>
     */
    @Test
    void refundedSaleTakesTheCashBackOutOfTheExpectedAmount() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        Order vendida = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), operator);
        Order estornada = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), operator);
        flushAndClear();

        orderUseCase.refundOrder(estornada.id(), "cliente desistiu", operator);
        flushAndClear();

        // 100 (abertura) + 22 + 22 (as duas vendas) − 22 (a cédula que voltou) = 122.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("122.00"), operator);

        assertThat(closed.expectedAmount()).isEqualByComparingTo("122.00");
        assertThat(closed.diverges()).isFalse();
        // A venda que ficou de pé continua contada — o estorno tira uma, não zera o turno.
        assertThat(pdvUseCase.getOrder(vendida.id()).status()).isEqualTo(OrderStatus.CONCLUIDO);
    }

    @Test
    void movementsNetOutAgainstEachOther() {
        String operator = "caixa-" + uniqueSuffix();
        String warehouseCode = givenStockedWarehouse(operator).split("\\|")[0];

        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);
        pdvUseCase.registerCashMovement(session.id(), CashMovementType.SANGRIA, new BigDecimal("80.00"),
                "cofre", operator);
        pdvUseCase.registerCashMovement(session.id(), CashMovementType.SUPRIMENTO, new BigDecimal("30.00"),
                "reforço de troco", operator);
        flushAndClear();

        assertThat(pdvUseCase.listCashMovements(session.id(), 0, 50).content()).hasSize(2);
        // PDV-C012 — a rota passou a ser paginada; o teto vale mesmo com dois movimentos.
        assertThat(pdvUseCase.listCashMovements(session.id(), 0, 1).content()).hasSize(1);
        assertThat(pdvUseCase.listCashMovements(session.id(), 0, 1).totalElements()).isEqualTo(2);
        // 100 − 80 + 30 = 50. O sinal vem do tipo, não do valor gravado.
        assertThat(pdvUseCase.closeSession(session.id(), new BigDecimal("50.00"), operator)
                .expectedAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    void openSession_refusesASecondRegisterForTheSameOperator() {
        String operator = "caixa-" + uniqueSuffix();
        String warehouseCode = givenStockedWarehouse(operator).split("\\|")[0];

        pdvUseCase.openSession(operator, BigDecimal.TEN, warehouseCode);
        flushAndClear();

        assertThatThrownBy(() -> pdvUseCase.openSession(operator, BigDecimal.TEN, warehouseCode))
                .isInstanceOf(CashRegisterSessionAlreadyOpenException.class);
    }

    @Test
    void anotherOperatorCannotSellOnSomeoneElsesRegister() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.TEN, setup[0]);
        flushAndClear();

        assertThatThrownBy(() -> pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), "outro-caixa"))
                .isInstanceOf(CashRegisterSessionNotOwnedException.class);
    }

    @Test
    void sessionOrdersAreListedForTheSession() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.TEN, setup[0]);
        pdvUseCase.registerSale(session.id(), null, List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), operator);
        pdvUseCase.registerSale(session.id(), null, List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), operator);
        flushAndClear();

        assertThat(pdvUseCase.listSessionOrders(session.id(), 0, 20).totalElements()).isEqualTo(2L);
    }

    /**
     * PDV-C013 — {@code CashRegisterRepositoryImpl.findAll} paginava <b>sem {@code Sort}</b>, e
     * paginação sem {@code ORDER BY} não tem ordem determinística: a mesma sessão podia sair em
     * duas páginas enquanto outra sumia, e o cliente nunca saberia.
     *
     * <p>Percorre todas as páginas e exige que cada sessão apareça <b>exatamente uma vez</b> — é a
     * propriedade que importa, e ela não depende de quantas sessões outros testes deixaram na base.</p>
     */
    @Test
    void listSessions_paginatesWithoutRepeatingOrDroppingSessions() {
        String suffix = uniqueSuffix();
        String warehouseCode = givenStockedWarehouse("caixa-seed-" + suffix).split("\\|")[0];
        List<Long> criadas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            criadas.add(pdvUseCase.openSession("caixa-" + suffix + "-" + i, BigDecimal.TEN, warehouseCode).id());
        }
        flushAndClear();

        long total = pdvUseCase.listSessions(0, 1).totalElements();
        List<Long> vistas = new ArrayList<>();
        for (int page = 0; page * 3 < total; page++) {
            pdvUseCase.listSessions(page, 3).content()
                    .forEach(sessao -> vistas.add(sessao.id()));
        }

        // Nenhuma repetida em páginas diferentes...
        assertThat(vistas).doesNotHaveDuplicates();
        // ...e nenhuma das cinco perdida entre as páginas.
        assertThat(vistas).containsAll(criadas);
        // Mais recentes primeiro, que é a ordem que a rota passou a declarar.
        assertThat(vistas).isSortedAccordingTo(Comparator.reverseOrder());
    }

    /** PDV-F026 — status e operador filtram no banco, e o período incide sobre openedAt. */
    @Test
    void listSessions_filtersByStatusOperatorAndOpenedAt() {
        String suffix = uniqueSuffix();
        String warehouseCode = givenStockedWarehouse("caixa-seed-" + suffix).split("\\|")[0];
        String operador = "caixa-filtro-" + suffix;
        CashRegisterSession aberta = pdvUseCase.openSession(operador, BigDecimal.TEN, warehouseCode);
        flushAndClear();

        assertThat(pdvUseCase.listSessions(new CashRegisterSessionFilter(CashRegisterSession.Status.OPEN, null, null,
                operador), 0, 20).content()).extracting(CashRegisterSession::id).containsExactly(aberta.id());
        assertThat(pdvUseCase.listSessions(new CashRegisterSessionFilter(CashRegisterSession.Status.CLOSED, null,
                null, operador), 0, 20).content()).isEmpty();
        assertThat(pdvUseCase.listSessions(new CashRegisterSessionFilter(null, aberta.openedAt().plusSeconds(60),
                null, operador), 0, 20).content()).isEmpty();
        assertThat(pdvUseCase.listSessions(new CashRegisterSessionFilter(null, aberta.openedAt().minusSeconds(60),
                aberta.openedAt().plusSeconds(60), operador), 0, 20).content()).hasSize(1);
    }

    @Test
    void orderNumbersAreUniqueAcrossSales() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.TEN, setup[0]);
        String first = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), operator).orderNumber();
        String second = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)), cash("22.00"), operator).orderNumber();

        assertThat(first).isNotEqualTo(second);
    }

    // ── PDV-F022: entrega persistida em order_delivery (tabela secundária) ───────────────────

    @Test
    void saleWithEntrega_persistsDelivery_patchesTrackingLater_andFollowsTheShippingPipeline() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("0.00"), setup[0]);
        flushAndClear();

        OrderDelivery delivery = new OrderDelivery(DeliveryType.ENTREGA,
                new DeliveryAddress("Rua A", "10", "Casa", "58000-000", "Centro", "João Pessoa", "PB", "Brasil", null),
                DeliveryMethod.CORREIOS, null, null, null, null, null, new BigDecimal("12.50"));
        Order sold = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(setup[1], new BigDecimal("2.000"), null, "Embrulhar para presente")),
                cash("56.50"), operator, false, delivery);
        flushAndClear();

        Order reloaded = orderUseCase.getOrder(sold.id());
        assertThat(reloaded.status()).isEqualTo(OrderStatus.RESERVADO);
        assertThat(reloaded.totalPayable()).isEqualByComparingTo("56.50");
        assertThat(reloaded.delivery()).isEqualTo(delivery);
        assertThat(reloaded.items().get(0).notes()).isEqualTo("Embrulhar para presente");

        // Rastreio chega depois da postagem; o resto da entrega fica como estava.
        orderUseCase.updateDelivery(sold.id(), new OrderDelivery.Patch(null, null, null, null, null, null, null,
                "BR123456789BR", null), operator);
        flushAndClear();
        Order patched = orderUseCase.getOrder(sold.id());
        assertThat(patched.delivery().trackingCode()).isEqualTo("BR123456789BR");
        assertThat(patched.delivery().address().complement()).isEqualTo("Casa");
        assertThat(patched.delivery().fee()).isEqualByComparingTo("12.50");

        orderUseCase.changeStatus(sold.id(), OrderStatus.SEPARADO, operator);
        orderUseCase.changeStatus(sold.id(), OrderStatus.ENVIADO, operator);
        orderUseCase.changeStatus(sold.id(), OrderStatus.ENTREGUE, operator);
        flushAndClear();
        Order delivered = orderUseCase.getOrder(sold.id());
        assertThat(delivered.status()).isEqualTo(OrderStatus.ENTREGUE);
        assertThat(delivered.shippedAt()).isNotNull();
        assertThat(delivered.delivery().trackingCode()).isEqualTo("BR123456789BR");

        // A listagem da sessão lê a entrega na mesma consulta (LEFT JOIN da tabela secundária).
        assertThat(pdvUseCase.listSessionOrders(session.id(), 0, 10).content())
                .singleElement().satisfies(o -> assertThat(o.delivery()).isNotNull());
    }

    @Test
    void saleWithoutDelivery_readsBackWithNullDelivery() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("0.00"), setup[0]);
        Order sold = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(setup[1], new BigDecimal("1.000"), null)), cash("22.00"), operator);
        flushAndClear();

        Order reloaded = orderUseCase.getOrder(sold.id());
        assertThat(reloaded.delivery()).isNull();
        assertThat(reloaded.status()).isEqualTo(OrderStatus.CONCLUIDO);
        Number rows = (Number) em.createNativeQuery("SELECT COUNT(*) FROM order_delivery WHERE order_id = :id")
                .setParameter("id", sold.id()).getSingleResult();
        assertThat(rows.intValue()).isZero();
    }

    // ── PDV-F030: correção da forma de pagamento ────────────────────────────────────────────

    private static BigDecimal netOf(List<PdvUseCase.PaymentTotal> totals, PaymentMethod method) {
        return totals.stream().filter(t -> t.method() == method).findFirst().orElseThrow().netAmount();
    }

    @Test
    void correctPayments_movesTheAmountBetweenMethodsAndKeepsTheWrongLineAsHistory() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), setup[0]);
        // 2 carvões a 22,00 = 44,00, pagos com 50 em dinheiro: 6 de troco.
        Order sold = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(setup[1], new BigDecimal("2.000"), null)), cash("50.00"), operator);
        flushAndClear();

        orderUseCase.correctPayments(sold.id(),
                List.of(new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("44.00"), null)),
                "Cliente pagou no débito", "ana", false);
        flushAndClear();

        List<PdvUseCase.PaymentTotal> totals = pdvUseCase.getSessionPaymentTotals(session.id());
        assertThat(netOf(totals, PaymentMethod.DINHEIRO)).isEqualByComparingTo("0.00");
        assertThat(netOf(totals, PaymentMethod.DEBITO)).isEqualByComparingTo("44.00");

        assertThat(orderUseCase.getOrder(sold.id()).changeAmount()).isNull();
        assertThat(orderUseCase.getOrderPayments(sold.id()))
                .extracting(p -> p.method(), p -> p.status())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PaymentMethod.DINHEIRO, PaymentStatus.CORRECTED),
                        org.assertj.core.groups.Tuple.tuple(PaymentMethod.DEBITO, PaymentStatus.CAPTURED));
        assertThat(orderUseCase.getPaymentHistory(sold.id())).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.before()).extracting(p -> p.method()).containsExactly(PaymentMethod.DINHEIRO);
                    assertThat(entry.after()).extracting(p -> p.method()).containsExactly(PaymentMethod.DEBITO);
                });

        // O fechamento conta só o fundo de troco: a venda saiu do dinheiro.
        CashRegisterSession closed = pdvUseCase.closeSession(session.id(), new BigDecimal("100.00"), operator);
        assertThat(closed.expectedAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void correctPayments_afterClose_recordsTheAdjustmentOnTheClosedSession() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("0.00"), setup[0]);
        Order sold = pdvUseCase.registerSale(session.id(), null,
                List.of(new SaleItemCommand(setup[1], new BigDecimal("2.000"), null)),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("44.00"), null)), operator);
        pdvUseCase.closeSession(session.id(), BigDecimal.ZERO, operator);
        flushAndClear();

        orderUseCase.correctPayments(sold.id(),
                List.of(new PaymentCommand(PaymentMethod.CREDITO, new BigDecimal("44.00"), 2)),
                "foi crédito em 2x", "gerente", true);
        flushAndClear();

        List<?> rows = em.createNativeQuery(
                        "SELECT method, delta_amount FROM cash_session_adjustment WHERE session_id = :id ORDER BY method")
                .setParameter("id", session.id()).getResultList();
        assertThat(rows).hasSize(2);
        assertThat(((Object[]) rows.get(0))[0]).isEqualTo("CREDITO");
        assertThat(new BigDecimal(((Object[]) rows.get(0))[1].toString())).isEqualByComparingTo("44.00");
        assertThat(((Object[]) rows.get(1))[0]).isEqualTo("PIX");
        assertThat(new BigDecimal(((Object[]) rows.get(1))[1].toString())).isEqualByComparingTo("-44.00");
    }
}
