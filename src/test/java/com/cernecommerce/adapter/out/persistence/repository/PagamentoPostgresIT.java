package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pagamentos contra Postgres real: as CHECKs da V136 (correção de forma de pagamento) só existem no
 * Flyway — o H2 do perfil dev monta o schema pelas entidades.
 * Habilitar com: {@code ENABLE_TC=true ./mvnw test -Dapi.version=1.44}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class PagamentoPostgresIT {

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
    @Autowired OrderUseCase orderUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Depósito + produto a 22,00 + saldo, e a sessão aberta de hoje. Devolve [sessionId, sku, operador]. */
    private String[] givenOpenSessionWithStock() {
        String s = suffix();
        String operator = "caixa-" + s;
        estoqueUseCase.createWarehouse("LOJA-" + s, "Loja " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct("CARV-" + s, "Carvão " + s, "Carvões", List.of(),
                Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")));
        estoqueUseCase.adjustStock("CARV-" + s, "LOJA-" + s, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga", operator);
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("0.00"), "LOJA-" + s);
        return new String[] {String.valueOf(session.id()), "CARV-" + s, operator};
    }

    private Order sell(String[] ctx, PaymentMethod method, String amount) {
        return pdvUseCase.registerSale(Long.valueOf(ctx[0]), null,
                List.of(new SaleItemCommand(ctx[1], new BigDecimal("1.000"), null)),
                List.of(new PaymentCommand(method, new BigDecimal(amount), null)), ctx[2]);
    }

    @Test
    void correction_writesCorrectedAndNewRowsThatSatisfyTheV136Checks() {
        String[] ctx = givenOpenSessionWithStock();
        Order sold = sell(ctx, PaymentMethod.PIX, "22.00");

        orderUseCase.correctPayments(sold.id(),
                List.of(new PaymentCommand(PaymentMethod.DEBITO, new BigDecimal("22.00"), null)),
                "foi débito", "ana", false);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT method, status, captured_at, correction_id, origin_correction_id, corrected_by "
                        + "FROM order_payment WHERE order_id = ? ORDER BY id", sold.id());
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("method", "PIX").containsEntry("status", "CORRECTED")
                .containsEntry("corrected_by", "ana");
        assertThat(rows.get(0).get("captured_at")).isNotNull();
        assertThat(rows.get(1)).containsEntry("method", "DEBITO").containsEntry("status", "CAPTURED");
        assertThat(rows.get(1).get("origin_correction_id")).isEqualTo(rows.get(0).get("correction_id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_payment_correction WHERE order_id = ?",
                Integer.class, sold.id())).isEqualTo(1);
    }

    @Test
    void correctedRowWithoutCorrectionReference_isRejectedByTheCheck() {
        String[] ctx = givenOpenSessionWithStock();
        Order sold = sell(ctx, PaymentMethod.PIX, "22.00");

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE order_payment SET status = 'CORRECTED' WHERE order_id = ?", sold.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void newPermissionsAreSeededAndGrantedToTheRightRoles() {
        assertThat(jdbc.queryForList("""
                SELECT r.name FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.name = 'ORDER_PAYMENT_CORRECT' ORDER BY r.name""", String.class))
                .containsExactly("ROLE_ADMIN", "ROLE_ATENDENTE");
        assertThat(jdbc.queryForList("""
                SELECT r.name FROM roles r
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE p.name = 'ORDER_PAYMENT_CORRECT_CLOSED'""", String.class))
                .containsExactly("ROLE_ADMIN");
    }
}
