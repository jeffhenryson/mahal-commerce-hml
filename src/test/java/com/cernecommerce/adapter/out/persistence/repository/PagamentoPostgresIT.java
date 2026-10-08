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
 * Pagamentos contra Postgres real: as CHECKs da V136 (correção de forma de pagamento) e da V137
 * ("Marcar") só existem no Flyway — o H2 do perfil dev monta o schema pelas entidades.
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
    @Autowired com.cernecommerce.core.ports.in.CrmUseCase crmUseCase;
    @Autowired com.cernecommerce.core.ports.in.ReceivableUseCase receivableUseCase;
    @Autowired com.cernecommerce.core.ports.out.crm.TagRepository tagRepository;

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

    // ── CRM-F010: "Marcar" (V137) ───────────────────────────────────────────────────────────

    private Long givenVip(String creditLimit) {
        String cpf = String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
        Long id = crmUseCase.createCustomer("VIP " + suffix(), null, null, cpf, "PDV").id();
        var tag = tagRepository.findByNome("VIP")
                .orElseGet(() -> tagRepository.save(new com.cernecommerce.core.domain.model.crm.Tag(null, "VIP")));
        crmUseCase.addTagToCustomer(id, tag.id());
        receivableUseCase.setCreditLimit(id, com.cernecommerce.core.domain.model.recebivel.OnAccountChannel.BALCAO,
                new BigDecimal(creditLimit), "gerente");
        return id;
    }

    @Test
    void v142_seedsTheChannelDefaults_andRefusesAnUnknownChannel() {
        assertThat(jdbc.queryForList("SELECT config_key FROM system_config WHERE config_key LIKE "
                + "'pdv.on-account.default-credit-limit.%' ORDER BY config_key", String.class))
                .containsExactly("pdv.on-account.default-credit-limit.balcao",
                        "pdv.on-account.default-credit-limit.mesa");
        Long vip = givenVip("10.00");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO customer_credit_limit (customer_id, channel, credit_limit, updated_by, updated_at) "
                                + "VALUES (?, 'DELIVERY', 1, 'x', now())", vip))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void onAccountSale_paymentAndOverdueJob_againstTheRealSchema() {
        String[] ctx = givenOpenSessionWithStock();
        Long vip = givenVip("100.00");
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));

        Order sold = pdvUseCase.registerSale(Long.valueOf(ctx[0]), vip,
                List.of(new SaleItemCommand(ctx[1], new BigDecimal("1.000"), null)),
                List.of(PaymentCommand.onAccount(new BigDecimal("22.00"), today)), ctx[2]);

        Map<String, Object> line = jdbc.queryForMap(
                "SELECT method, status, due_date, captured_at FROM order_payment WHERE order_id = ?", sold.id());
        assertThat(line).containsEntry("method", "MARCADO").containsEntry("status", "ON_ACCOUNT");
        assertThat(line.get("due_date")).isNotNull();
        assertThat(line.get("captured_at")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM receivable_item ri JOIN customer_receivable r "
                + "ON r.id = ri.receivable_id WHERE r.order_id = ?", Integer.class, sold.id())).isEqualTo(1);

        // Resumo e listagem com filtros de data (Specification + LocalDate no Postgres).
        assertThat(receivableUseCase.summary(null, null))
                .anySatisfy(row -> assertThat(row.customerId()).isEqualTo(vip));
        assertThat(receivableUseCase.list(new com.cernecommerce.core.domain.model.recebivel.ReceivableFilter(
                vip, null, null, today, today, null, null, null, null), 0, 20).content()).hasSize(1);
        // V142: o limite é por (cliente, canal), e o filtro de canal/busca roda contra o schema real.
        assertThat(jdbc.queryForObject("SELECT channel FROM customer_credit_limit WHERE customer_id = ?",
                String.class, vip)).isEqualTo("BALCAO");
        assertThat(receivableUseCase.list(new com.cernecommerce.core.domain.model.recebivel.ReceivableFilter(
                null, null, null, null, null, null, null,
                com.cernecommerce.core.domain.model.recebivel.OnAccountChannel.BALCAO, "VIP"), 0, 200).content())
                .anySatisfy(v -> assertThat(v.receivable().orderId()).isEqualTo(sold.id()));

        // Simula o vencimento e roda o job: o marcado passa a VENCIDO e bloqueia novo marcar.
        jdbc.update("UPDATE customer_receivable SET due_date = due_date - 1 WHERE order_id = ?", sold.id());
        assertThat(receivableUseCase.markOverdue()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM customer_receivable WHERE order_id = ?", String.class,
                sold.id())).isEqualTo("VENCIDO");
        assertThat(receivableUseCase.eligibility(vip, null, true).reasons()).contains("CUSTOMER_HAS_OVERDUE");

        // Quitação em PIX no mesmo caixa: zera e volta a QUITADO.
        var result = receivableUseCase.pay(Long.valueOf(ctx[0]), ctx[2], vip,
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("22.00"), null)), null);
        assertThat(result.openBalanceAfter()).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("SELECT status FROM customer_receivable WHERE order_id = ?", String.class,
                sold.id())).isEqualTo("QUITADO");
    }

    @Test
    void marcadoWithoutDueDate_isRejectedByTheCheck() {
        String[] ctx = givenOpenSessionWithStock();
        Order sold = sell(ctx, PaymentMethod.PIX, "22.00");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO order_payment (order_id, method, amount, status, created_at) "
                + "VALUES (?, 'MARCADO', 10, 'ON_ACCOUNT', now())", sold.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
