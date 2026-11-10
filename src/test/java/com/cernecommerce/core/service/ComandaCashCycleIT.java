package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionHasOpenComandasException;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comanda de mesa de ponta a ponta contra banco real (PDV-F009).
 *
 * <p>É o teste que prova que a baixa de estoque é <b>imediata por item</b>, não só no fechamento —
 * ver o javadoc de {@code ComandaService}. Os testes de unidade mockam {@code EstoqueUseCase}, então
 * nada exercitava se o saldo de verdade caía a cada lançamento, nem o mapeamento de
 * {@code comanda}/{@code comanda_item} contra o ciclo completo (abrir → lançar → fechar).</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
// PDV-F021 — a sessão por produto (OPEN_ROSH, SABOR_EXTRA, TROCA, lata) foi substituída pelo cardápio
// e vem desligada por padrão. Estes cenários continuam valendo para o caminho legado, que segue
// religável por propriedade; o fluxo novo está em SessionMenuFlowIT.
@TestPropertySource(properties = "pdv.sessao.legacy-enabled=true")
class ComandaCashCycleIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;
    @Autowired CashbackUseCase cashbackUseCase;
    @Autowired com.cernecommerce.core.ports.out.pedido.OrderRepository orderRepository;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private String uniqueSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Depósito + produto precificado + saldo inicial, mesmo padrão de {@code PdvCashCycleIT}. */
    private String givenStockedWarehouse(String operator) {
        String suffix = uniqueSuffix();
        String warehouseCode = "LOUNGE-" + suffix;
        String sku = "ESS-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", operator);
        return warehouseCode + "|" + sku;
    }

    @Test
    void fullCycle_openAddTwoItemsWithImmediateDebitAndCloseWithSplitPayment() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        flushAndClear();

        // 1. Abre a comanda.
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 4", operator);
        assertThat(comanda.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(comanda.warehouseCode()).isEqualTo(warehouseCode);
        flushAndClear();

        // 2. Lança o primeiro item — debita 1 unidade NA HORA, não no fechamento.
        comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("49.000");

        // 3. Lança o segundo item — debita mais 1, mesmo antes de a comanda fechar.
        Comanda comTwoItems = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");
        assertThat(comTwoItems.items()).hasSize(2);
        assertThat(comTwoItems.runningTotal()).isEqualByComparingTo("50.00");

        // 4. Fecha dividindo o pagamento: 15 no débito + 10 em dinheiro para uma comanda de 50 —
        // espera 400 de pagamento insuficiente, então cobre o total exato de duas formas.
        List<PaymentCommand> split = List.of(
                new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("30.00"), null),
                new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("20.00"), null));
        Order order = comandaUseCase.closeComanda(comanda.id(), split, null, false, null, operator);
        flushAndClear();

        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        // PDV-F010: o pedido da mesa NASCE MESA — o canal é imutável, não vira MESA depois.
        assertThat(order.channel()).isEqualTo(SalesChannel.MESA);
        assertThat(order.comandaId()).isEqualTo(comanda.id());
        assertThat(order.tableLabel()).isEqualTo("Mesa 4");
        assertThat(order.netAmount()).isEqualByComparingTo("50.00");
        assertThat(order.items()).hasSize(2);
        assertThat(pdvUseCase.getOrderPayments(order.id())).hasSize(2);

        // 5. Sem novo débito no fechamento — o saldo continua exatamente onde os lançamentos
        // incrementais deixaram.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");

        Comanda fechada = comandaUseCase.getComanda(comanda.id());
        assertThat(fechada.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(fechada.orderId()).isEqualTo(order.id());
        assertThat(comandaUseCase.listOpenComandas(session.id(), null, 0, 50).content()).isEmpty();
    }

    @Test
    void cancelComanda_returnsStockForEveryLaunchedItem() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 9", operator);
        comandaUseCase.addItem(comanda.id(), sku, new BigDecimal("2.000"), operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("48.000");

        Comanda cancelada = comandaUseCase.cancelComanda(comanda.id(), operator);
        flushAndClear();

        assertThat(cancelada.status()).isEqualTo(ComandaStatus.CANCELADA);
        // Devolveu ao estoque exatamente o que tinha sido debitado — de volta a 50.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("50.000");
        assertThat(comandaUseCase.listOpenComandas(session.id(), null, 0, 50).content()).isEmpty();
    }

    /**
     * O ciclo que a PDV-F010 existe para permitir: mesa com cliente, duplo em cortesia, e o pedido
     * nascendo no canal MESA com cashback — que a mesa anônima do PDV-F009 nunca gerava.
     */
    @Test
    void fullCycle_mesaComClienteEDuploEmCortesia_geraPedidoMesaComCashback() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        // Vira produto de sessão: é o que libera SABOR_EXTRA no lançamento (NOT_A_SESSION_PRODUCT
        // recusaria antes disso).
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 10, new BigDecimal("60.00")));
        Customer cliente = crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                uniqueCpf(), "mesa");
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 12", cliente.id(), operator);
        flushAndClear();
        assertThat(comanda.customerId()).isEqualTo(cliente.id());

        // 1. A sessão em si, cobrada pelo preço da variação do sabor.
        Comanda comSessao = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE,
                ConsumptionMode.NORMAL, false, null, operator);
        Long sessaoId = comSessao.items().get(0).id();
        flushAndClear();

        // 2. Segundo sabor na promo "pague 1 leve 2": não cobra, mas a essência sai do estoque.
        Comanda comDuplo = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE,
                ConsumptionMode.SABOR_EXTRA, true, sessaoId, operator);
        flushAndClear();

        assertThat(comDuplo.runningTotal()).isEqualByComparingTo("25.00");
        assertThat(comDuplo.items().get(1).linkedItemId()).isEqualTo(sessaoId);
        // EST-F027 — o produto tem sessionsPerUnit = 10, então as duas linhas saem da MESMA lata:
        // a primeira abre uma (50 → 49) e o sabor extra consome o segundo uso dela, não uma
        // segunda unidade. A cortesia continua saindo do estoque, só que medida em usos — que é o
        // ponto do teste. Antes de EST-F027 isto era 48,000, e a essência sumia 10x mais rápido
        // que a realidade.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("49.000");
        assertThat(estoqueUseCase.findOpenPackage(sku, warehouseCode).uses()).isEqualTo(2);

        Order order = comandaUseCase.closeComanda(comanda.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("25.00"), null)), null, false,
                null,
                operator);
        flushAndClear();

        assertThat(order.channel()).isEqualTo(SalesChannel.MESA);
        assertThat(order.customerId()).isEqualTo(cliente.id());
        assertThat(order.comandaId()).isEqualTo(comanda.id());
        assertThat(order.tableLabel()).isEqualTo("Mesa 12");
        assertThat(order.netAmount()).isEqualByComparingTo("25.00");
        assertThat(order.items()).hasSize(2);
        assertThat(order.items().get(1)).satisfies(cortesia -> {
            assertThat(cortesia.mode()).isEqualTo(ConsumptionMode.SABOR_EXTRA);
            assertThat(cortesia.courtesy()).isTrue();
            assertThat(cortesia.unitPrice()).isEqualByComparingTo("0.00");
            // Custo congelado normalmente: é o que faz a margem mostrar o prejuízo real da promo.
            assertThat(cortesia.costPrice()).isEqualByComparingTo("10.00");
        });

        // 25,00 líquido x 3% (taxa GLOBAL da V70) = 0,75, ainda em carência.
        assertThat(cashbackUseCase.getCustomerBalance(cliente.id()).pending())
                .isEqualByComparingTo("0.75");
    }

    /**
     * PDV-C005 — o beco sem saída, provado de ponta a ponta contra o banco real.
     *
     * <p>Antes desta barreira o fechamento passava, e a mesa que sobrava virava um objeto
     * intocável: {@code addItem} e {@code cancelComanda} exigem a sessão de origem ABERTA, então as
     * duas respondiam 409 para sempre, com a essência já debitada e sem nenhum caminho de
     * devolução. A regra existia só no cliente.</p>
     */
    @Test
    void closeSession_comMesaAberta_eRecusadoEAMesaContinuaOperavel() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 4", operator);
        comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();

        assertThatThrownBy(() -> pdvUseCase.closeSession(session.id(), BigDecimal.ZERO, "gerente"))
                .isInstanceOf(CashRegisterSessionHasOpenComandasException.class)
                .hasMessageContaining(String.valueOf(comanda.id()));
        flushAndClear();

        // O caixa continua ABERTO — e é isso que mantém a mesa operável.
        assertThat(pdvUseCase.getSession(session.id()).isOpen()).isTrue();

        // Prova do que a barreira protege: cancelar ainda funciona e devolve o estoque.
        comandaUseCase.cancelComanda(comanda.id(), operator);
        flushAndClear();
        assertThat(comandaUseCase.getComanda(comanda.id()).status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("50.000");

        // Resolvida a mesa, o caixa fecha normalmente.
        assertThat(pdvUseCase.closeSession(session.id(), BigDecimal.ZERO, "gerente").isOpen()).isFalse();
    }

    /**
     * PDV-F011 de ponta a ponta: o setup da mesa e o acréscimo do open rosh sobrevivem ao
     * fechamento e chegam ao pedido. Sem isso, a tela de Vendas &gt; Pedidos não responde "qual
     * pinça saiu com aquela mesa" — pergunta que só é feita depois de a mesa ter fechado.
     */
    @Test
    void fullCycle_openRoshComAcrescimoEComponentes_chegaInteiroNoPedido() {
        String suffix = uniqueSuffix();
        String operator = "caixa-" + suffix;
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        // openRoshPrice = 60,00 no PAI; o salePrice da variação continua 25,00.
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 10, new BigDecimal("60.00")));
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 15", operator);
        flushAndClear();

        String componentes = "Narguilé grande · Com filtro · Pinça P-02";
        Comanda comOpenRosh = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, componentes, new BigDecimal("15.00"), operator);
        flushAndClear();

        // 60,00 do PAI + 15,00 de acréscimo. Somar sobre a variação daria 40,00.
        assertThat(comOpenRosh.items().get(0).unitPrice()).isEqualByComparingTo("75.00");
        assertThat(comOpenRosh.items().get(0).surchargeAmount()).isEqualByComparingTo("15.00");
        assertThat(comOpenRosh.items().get(0).notes()).isEqualTo(componentes);
        assertThat(comOpenRosh.runningTotal()).isEqualByComparingTo("75.00");

        Order order = comandaUseCase.closeComanda(comanda.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("75.00"), null)), null, false,
                null,
                operator);
        flushAndClear();

        assertThat(order.channel()).isEqualTo(SalesChannel.MESA);
        assertThat(order.netAmount()).isEqualByComparingTo("75.00");
        assertThat(order.items().get(0)).satisfies(item -> {
            assertThat(item.notes()).isEqualTo(componentes);
            assertThat(item.surchargeAmount()).isEqualByComparingTo("15.00");
            assertThat(item.unitPrice()).isEqualByComparingTo("75.00");
            // Acréscimo é margem, não custo — o custo congelado não se mexeu.
            assertThat(item.costPrice()).isEqualByComparingTo("10.00");
            assertThat(item.marginAmount()).isEqualByComparingTo("65.00");
        });
    }

    /**
     * PDV-F014 + PDV-F015 contra banco real, no mesmo fechamento: o desconto rateado entre as
     * linhas e a taxa incidindo sobre o que sobra.
     *
     * <p>É aqui que a decisão de desenho aparece de ponta a ponta — o valor gravado em
     * {@code net_amount}, que quatro agregações somam como receita, <b>não</b> inclui os 10% do
     * garçom, mas o pagamento exigido e o dinheiro na gaveta incluem.</p>
     */
    @Test
    void fullCycle_mesaComDescontoRateadoETaxaDeServico() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        flushAndClear();

        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 9", operator);
        flushAndClear();
        // Quatro essências de 25,00 = 100,00 de conta.
        comandaUseCase.addItem(comanda.id(), sku, new BigDecimal("4"), operator);
        flushAndClear();

        // Desconto de 5,00 (dentro do teto de 5%) e taxa de serviço aplicada.
        // 100,00 − 5,00 = 95,00 de líquido; 10% disso = 9,50; total a pagar 104,50.
        Order order = comandaUseCase.closeComanda(comanda.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("105.00"), null)),
                new BigDecimal("5.00"), true, null, operator);
        flushAndClear();

        assertThat(order.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(order.channel()).isEqualTo(SalesChannel.MESA);
        assertThat(order.grossAmount()).isEqualByComparingTo("100.00");
        assertThat(order.discountAmount()).isEqualByComparingTo("5.00");
        // A RECEITA não enxerga a gorjeta.
        assertThat(order.netAmount()).isEqualByComparingTo("95.00");
        // A taxa fica em coluna própria, conferível.
        assertThat(order.serviceFeeAmount()).isEqualByComparingTo("9.50");
        assertThat(order.totalPayable()).isEqualByComparingTo("104.50");
        // Troco de 105,00 sobre 104,50 — calculado sobre o total COM taxa, não sobre o líquido.
        assertThat(order.changeAmount()).isEqualByComparingTo("0.50");

        // O desconto foi rateado: uma linha só, absorve os 5,00 inteiros.
        assertThat(order.items()).hasSize(1);
        assertThat(order.items().get(0).discountAmount()).isEqualByComparingTo("5.00");

        // Round-trip: a taxa sobrevive à persistência, não só ao objeto em memória.
        Order relido = orderRepository.findById(order.id()).orElseThrow();
        assertThat(relido.serviceFeeAmount()).isEqualByComparingTo("9.50");
        assertThat(relido.netAmount()).isEqualByComparingTo("95.00");
        assertThat(relido.totalPayable()).isEqualByComparingTo("104.50");
    }

    /**
     * A contrapartida da decisão: o dinheiro da taxa passa pela gaveta como qualquer outro, então
     * a conferência do fechamento tem que enxergá-lo. Ela soma {@code order_payment}, não
     * {@code net_amount} — é por isso que manter a taxa fora do líquido não quebrou o caixa.
     */
    @Test
    void closeSession_expectedAmountIncludesTheServiceFeePaidInCash() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        flushAndClear();
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 10", operator);
        flushAndClear();
        comandaUseCase.addItem(comanda.id(), sku, new BigDecimal("4"), operator);
        flushAndClear();

        // 100,00 de conta + 10,00 de taxa = 110,00, pagos em dinheiro.
        comandaUseCase.closeComanda(comanda.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("110.00"), null)),
                null, true, null, operator);
        flushAndClear();

        CashRegisterSession fechada = pdvUseCase.closeSession(session.id(), new BigDecimal("110.00"), operator);

        // 110,00, e não 100,00: a gorjeta está fisicamente na gaveta.
        assertThat(fechada.expectedAmount()).isEqualByComparingTo("110.00");
        assertThat(fechada.differenceAmount()).isEqualByComparingTo("0.00");
    }

    /**
     * PDV-F012 contra banco real: a linha sai, o estoque volta, e a mesa continua aberta — que é
     * exatamente o que cancelar a comanda inteira <b>não</b> permitia.
     */
    @Test
    void removeItem_returnsStockAndKeepsTheComandaOpen() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        flushAndClear();
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 11", operator);
        flushAndClear();
        comandaUseCase.addItem(comanda.id(), sku, new BigDecimal("2"), operator);
        Comanda comDoisLancamentos = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("47.000");
        Long primeiraLinha = comDoisLancamentos.items().get(0).id();

        Comanda depois = comandaUseCase.removeItem(comanda.id(), primeiraLinha, operator);
        flushAndClear();

        // Devolveu as 2 unidades da linha removida — e só elas.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("49.000");
        // A mesa segue ABERTA, com a outra linha: o cliente continua consumindo.
        assertThat(depois.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(depois.items()).hasSize(1);
        assertThat(depois.runningTotal()).isEqualByComparingTo("25.00");

        // E ainda fecha normalmente, cobrando só o que sobrou.
        Order order = comandaUseCase.closeComanda(comanda.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("25.00"), null)),
                null, false, null, operator);
        flushAndClear();
        assertThat(order.netAmount()).isEqualByComparingTo("25.00");
        assertThat(order.items()).hasSize(1);
    }

    /**
     * A cascata contra banco real, que é onde ela importa: {@code linked_item_id} é FK
     * auto-referente, então deixar a troca para trás violaria a constraint — o teste falharia no
     * flush, não numa asserção.
     */
    @Test
    void removeItem_dragsTheTrocaAndReturnsStockForBoth() {
        String operator = "caixa-" + uniqueSuffix();
        String[] setup = givenStockedWarehouse(operator).split("\\|");
        String warehouseCode = setup[0];
        String sku = setup[1];

        // Mesmo setup do teste de open rosh acima: openRoshPrice = 60,00 no produto.
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 10, new BigDecimal("60.00")));
        flushAndClear();

        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Comanda comanda = comandaUseCase.openComanda(session.id(), "Mesa 12", operator);
        flushAndClear();

        Comanda comSessao = comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE,
                ConsumptionMode.OPEN_ROSH, false, null, operator);
        flushAndClear();
        Long sessaoId = comSessao.items().get(0).id();
        comandaUseCase.addItem(comanda.id(), sku, BigDecimal.ONE, ConsumptionMode.TROCA, true,
                sessaoId, operator);
        flushAndClear();
        // EST-F027 — mesma leitura do teste do duplo em cortesia: uma lata aberta (50 → 49) e dois
        // usos gastos nela, não duas unidades.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("49.000");
        assertThat(estoqueUseCase.findOpenPackage(sku, warehouseCode).uses()).isEqualTo(2);

        Comanda depois = comandaUseCase.removeItem(comanda.id(), sessaoId, operator);
        flushAndClear();

        // As duas linhas saíram, e o que as duas consumiram voltou. O que volta é a CONTAGEM DE
        // USOS da lata, não a unidade: a lata está aberta na bancada, e devolvê-la ao saldo
        // inventaria estoque que não existe. Ver ComandaService.undoStock / releaseSession.
        assertThat(depois.items()).isEmpty();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("49.000");
        assertThat(estoqueUseCase.findOpenPackage(sku, warehouseCode).uses()).isZero();
    }

    private String uniqueCpf() {
        return String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
    }
}
