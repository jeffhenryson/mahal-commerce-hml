package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase.ComandaHistoryEntry;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Histórico e indicadores de mesas de ponta a ponta contra banco real (PDV-F029).
 *
 * <p>Os testes de unidade mockam o repositório, então nada provava que {@code closedBy} e
 * {@code cancelReason} chegam à tabela, que a Specification do histórico filtra de verdade, nem que
 * os pedidos parciais e o total da mesma mesa voltam juntos pelo {@code comanda_id}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
// Mesmo motivo de ComandaCashCycleIT: o lançamento por SKU de catálogo é o caminho legado.
@TestPropertySource(properties = "pdv.sessao.legacy-enabled=true")
class ComandaHistoryIT {

    @Autowired PdvUseCase pdvUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static List<PaymentCommand> dinheiro(String valor) {
        return List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal(valor), null));
    }

    private static ComandaHistoryFilter noDeposito(String warehouseCode) {
        return new ComandaHistoryFilter(null, null, null, null, null, null, null, warehouseCode);
    }

    /**
     * Uma mesa fechada em duas vezes (parcial com taxa + o resto sem taxa) e outra cancelada com
     * motivo, num depósito só deste teste — o filtro por depósito isola o que outros testes deixaram.
     */
    @Test
    void closedAndCancelledTables_showUpInHistoryDetailAndAnalytics() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String operator = "caixa-" + suffix;
        String warehouseCode = "HIST-" + suffix;
        String sku = "ESS-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("50.000"),
                "carga inicial", operator);
        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouseCode);
        Instant inicio = Instant.now().minus(1, ChronoUnit.MINUTES);

        // Mesa 4: duas linhas, a primeira paga à parte com os 10% (27,50), a segunda no fechamento (25).
        Comanda mesa4 = comandaUseCase.openComanda(session.id(), "Mesa 4", operator);
        comandaUseCase.addItem(mesa4.id(), sku, BigDecimal.ONE, operator);
        Comanda comDuas = comandaUseCase.addItem(mesa4.id(), sku, BigDecimal.ONE, operator);
        flushAndClear();
        Order parcial = comandaUseCase.closeComanda(mesa4.id(), dinheiro("27.50"), null, true,
                List.of(comDuas.items().get(0).id()), operator);
        flushAndClear();
        Order total = comandaUseCase.closeComanda(mesa4.id(), dinheiro("25.00"), null, false, null, operator);
        flushAndClear();

        // Mesa 7: cancelada com motivo.
        Comanda mesa7 = comandaUseCase.openComanda(session.id(), "Mesa 7", operator);
        comandaUseCase.cancelComanda(mesa7.id(), operator, "  Cliente desistiu ");
        flushAndClear();

        // ── Histórico ──
        PageResult<ComandaHistoryEntry> historico = comandaUseCase.listHistory(noDeposito(warehouseCode), 0, 50);
        assertThat(historico.totalElements()).isEqualTo(2);
        ComandaHistoryEntry fechada = historico.content().stream()
                .filter(e -> e.comanda().id().equals(mesa4.id())).findFirst().orElseThrow();
        assertThat(fechada.comanda().status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(fechada.closedBy()).isEqualTo(operator);
        assertThat(fechada.cancelReason()).isNull();
        // O pedido parcial volta junto com o do fechamento: os dois apontam para a comanda.
        assertThat(fechada.orders()).extracting(Order::id).containsExactlyInAnyOrder(parcial.id(), total.id());
        assertThat(fechada.totalPaid()).isEqualByComparingTo("52.50");
        assertThat(fechada.serviceFeeTotal()).isEqualByComparingTo("2.50");
        assertThat(fechada.durationMinutes()).isNotNull();

        ComandaHistoryEntry cancelada = historico.content().stream()
                .filter(e -> e.comanda().id().equals(mesa7.id())).findFirst().orElseThrow();
        assertThat(cancelada.comanda().status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(cancelada.closedBy()).isEqualTo(operator);
        assertThat(cancelada.cancelReason()).isEqualTo("Cliente desistiu");
        assertThat(cancelada.orders()).isEmpty();

        // Filtros: status, mesa sem caixa nem espaços, quem fechou.
        assertThat(comandaUseCase.listHistory(new ComandaHistoryFilter(null, null, ComandaStatus.CANCELADA, null,
                null, null, null, warehouseCode), 0, 50).content())
                .extracting(e -> e.comanda().id()).containsExactly(mesa7.id());
        assertThat(comandaUseCase.listHistory(new ComandaHistoryFilter(null, null, null, null, null, null,
                "  mesa 4 ", warehouseCode), 0, 50).content())
                .extracting(e -> e.comanda().id()).containsExactly(mesa4.id());
        assertThat(comandaUseCase.listHistory(new ComandaHistoryFilter(null, null, null, null, null, "outro",
                null, warehouseCode), 0, 50).content()).isEmpty();
        assertThat(comandaUseCase.listHistory(new ComandaHistoryFilter(Instant.now().plus(1, ChronoUnit.HOURS),
                null, null, null, null, null, null, warehouseCode), 0, 50).content()).isEmpty();

        // ── Detalhe ──
        ComandaHistoryEntry detalhe = comandaUseCase.getHistoryEntry(mesa4.id());
        assertThat(detalhe.paymentsByOrder()).containsOnlyKeys(parcial.id(), total.id());
        assertThat(detalhe.paymentsByOrder().get(parcial.id())).hasSize(1);
        assertThat(detalhe.paymentsByOrder().get(total.id())).hasSize(1);

        // ── Indicadores: só a FECHADA conta ──
        ComandaUseCase.ComandaAnalytics indicadores = comandaUseCase.analytics(inicio,
                Instant.now().plus(1, ChronoUnit.MINUTES), warehouseCode);
        assertThat(indicadores.mesas()).isEqualTo(1);
        assertThat(indicadores.receitaTotal()).isEqualByComparingTo("52.50");
        assertThat(indicadores.taxaServicoTotal()).isEqualByComparingTo("2.50");
        assertThat(indicadores.porMesa()).singleElement()
                .satisfies(m -> assertThat(m.tableLabel()).isEqualTo("Mesa 4"));
        assertThat(indicadores.porAtendente()).singleElement()
                .satisfies(a -> assertThat(a.username()).isEqualTo(operator));
    }
}
