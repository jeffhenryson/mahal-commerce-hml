package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.ComandaUseCase.ComandaHistoryEntry;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-F029 contra Postgres real: a V138 ({@code closed_by}, {@code cancel_reason} e o índice
 * {@code (status, closed_at)}) só existe no Flyway, e o recorte por data é onde um {@code Instant}
 * nulo em JPQL viraria {@code bytea} no Postgres — é por isso que o histórico usa Specification.
 * Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
@TestPropertySource(properties = "pdv.sessao.legacy-enabled=true")
class ComandaHistoryPostgresIT {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        r.add("management.health.redis.enabled", () -> "false");
        r.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired PdvUseCase pdvUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired JdbcTemplate jdbc;

    private void closedAt(Long comandaId, String instant) {
        jdbc.update("UPDATE comanda SET closed_at = ? WHERE id = ?", Timestamp.from(Instant.parse(instant)),
                comandaId);
    }

    private static ComandaHistoryFilter periodo(Instant from, Instant to, String warehouseCode) {
        return new ComandaHistoryFilter(from, to, null, null, null, null, null, warehouseCode);
    }

    @Test
    void history_filtersByClosingDate_ordersNewestFirst_andAnalyticsReadsTheOrders() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        String operator = "caixa-" + s;
        String warehouseCode = "LOJA-" + s;
        String sku = "CARV-" + s;
        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Carvão " + s, "Carvões", List.of(),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"), "carga",
                operator);
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("0.00"), warehouseCode);

        // Três canceladas, com o encerramento empurrado para datas conhecidas.
        Comanda janeiro10 = comandaUseCase.openComanda(session.id(), "Mesa 1", operator);
        Comanda janeiro20 = comandaUseCase.openComanda(session.id(), "Mesa 2", operator);
        Comanda fevereiro5 = comandaUseCase.openComanda(session.id(), "Mesa 3", operator);
        comandaUseCase.cancelComanda(janeiro10.id(), operator, "Cliente desistiu");
        comandaUseCase.cancelComanda(janeiro20.id(), operator, null);
        comandaUseCase.cancelComanda(fevereiro5.id(), operator, null);
        closedAt(janeiro10.id(), "2026-01-10T15:00:00Z");
        closedAt(janeiro20.id(), "2026-01-20T15:00:00Z");
        closedAt(fevereiro5.id(), "2026-02-05T15:00:00Z");

        assertThat(jdbc.queryForObject("SELECT closed_by FROM comanda WHERE id = ?", String.class, janeiro10.id()))
                .isEqualTo(operator);
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM comanda WHERE id = ?", String.class,
                janeiro10.id())).isEqualTo("Cliente desistiu");

        // Sem período: as três, da mais recente para a mais antiga.
        assertThat(comandaUseCase.listHistory(periodo(null, null, warehouseCode), 0, 50).content())
                .extracting(e -> e.comanda().id())
                .containsExactly(fevereiro5.id(), janeiro20.id(), janeiro10.id());
        // Período fechado.
        assertThat(comandaUseCase.listHistory(periodo(Instant.parse("2026-01-15T00:00:00Z"),
                Instant.parse("2026-01-31T23:59:59Z"), warehouseCode), 0, 50).content())
                .extracting(e -> e.comanda().id()).containsExactly(janeiro20.id());
        // Só um dos lados — o caso em que o parâmetro nulo quebraria a consulta JPQL no Postgres.
        assertThat(comandaUseCase.listHistory(periodo(Instant.parse("2026-01-15T00:00:00Z"), null,
                warehouseCode), 0, 50).content())
                .extracting(e -> e.comanda().id()).containsExactly(fevereiro5.id(), janeiro20.id());
        assertThat(comandaUseCase.listHistory(periodo(null, Instant.parse("2026-01-15T00:00:00Z"),
                warehouseCode), 0, 50).content())
                .extracting(e -> e.comanda().id()).containsExactly(janeiro10.id());
        // Paginação: totalElements é o total, a página traz o tamanho pedido.
        var pagina = comandaUseCase.listHistory(periodo(null, null, warehouseCode), 0, 2);
        assertThat(pagina.totalElements()).isEqualTo(3);
        assertThat(pagina.content()).hasSize(2);

        // Uma mesa fechada de verdade, para os indicadores lerem pedido e item pelo comanda_id.
        Comanda mesa = comandaUseCase.openComanda(session.id(), "Mesa 4", operator);
        comandaUseCase.addItem(mesa.id(), sku, BigDecimal.ONE, operator);
        comandaUseCase.closeComanda(mesa.id(),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("22.00"), null)), null, false,
                null, operator);

        ComandaHistoryEntry detalhe = comandaUseCase.getHistoryEntry(mesa.id());
        assertThat(detalhe.comanda().status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(detalhe.closedBy()).isEqualTo(operator);
        assertThat(detalhe.orders()).hasSize(1);
        assertThat(detalhe.paymentsByOrder().values()).singleElement().satisfies(p -> assertThat(p).hasSize(1));

        Instant agora = Instant.now();
        ComandaUseCase.ComandaAnalytics indicadores = comandaUseCase.analytics(agora.minus(1, ChronoUnit.HOURS),
                agora.plus(1, ChronoUnit.HOURS), warehouseCode);
        assertThat(indicadores.mesas()).isEqualTo(1);
        assertThat(indicadores.receitaTotal()).isEqualByComparingTo("22.00");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname = "
                + "'idx_comanda_status_closed_at'", Integer.class)).isEqualTo(1);
    }

    /**
     * PDV-F034/F036 — a V139: "comprou na loja?" grava e filtra o histórico (Boolean nulo na
     * Specification não pode virar parâmetro), a permissão nova existe e o CHECK do pay_later recusa
     * a marca em linha de catálogo.
     */
    @Test
    void storePurchase_filtersTheHistory_andPayLaterIsOnlyForSessionLines() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        String operator = "caixa-" + s;
        String warehouseCode = "LOJA-" + s;
        estoqueUseCase.createWarehouse(warehouseCode, "Loja " + s, WarehouseType.LOJA_FISICA);
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("0.00"), warehouseCode);
        Comanda comprou = comandaUseCase.openComanda(session.id(), "Mesa 1", operator);
        Comanda naoRespondeu = comandaUseCase.openComanda(session.id(), "Mesa 2", operator);
        comandaUseCase.cancelComanda(comprou.id(), operator, null);
        comandaUseCase.cancelComanda(naoRespondeu.id(), operator, null);
        comandaUseCase.recordStorePurchase(comprou.id(), true, operator);

        assertThat(jdbc.queryForObject("SELECT bought_in_store_by FROM comanda WHERE id = ?", String.class,
                comprou.id())).isEqualTo(operator);
        assertThat(comandaUseCase.listHistory(new ComandaHistoryFilter(null, null, null, null, null, null, null,
                warehouseCode, true), 0, 50).content()).extracting(e -> e.comanda().id()).containsExactly(comprou.id());
        assertThat(comandaUseCase.listHistory(new ComandaHistoryFilter(null, null, null, null, null, null, null,
                warehouseCode, false), 0, 50).content()).isEmpty();
        assertThat(comandaUseCase.listHistory(periodo(null, null, warehouseCode), 0, 50).content()).hasSize(2);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions WHERE name = 'PDV_SESSION_PAY_LATER'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                + "WHERE conname = 'ck_comanda_item_pay_later_by_mode'", String.class))
                .contains("SESSAO").contains("ROSH_EXTRA");
    }
}
