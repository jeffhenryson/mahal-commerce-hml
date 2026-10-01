package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;
import com.cernecommerce.core.domain.model.crm.CustomerMatchField;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CrmUseCase;
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
 * CRM-C007 / PDV-F022 contra Postgres real: V129 ({@code phone_normalized}) e V130
 * ({@code order_delivery}, com as CHECKs que espelham {@code OrderDelivery}) só existem no Flyway —
 * o H2 do perfil dev monta o schema pelas entidades, então é aqui que o mapeamento da tabela
 * secundária de {@code OrderEntity} é provado contra o DDL de verdade.
 * Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class PdvDeliveryPostgresIT {

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
    @Autowired CrmUseCase crmUseCase;
    @Autowired JdbcTemplate jdbc;

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniquePhone() {
        return "839" + String.format("%08d", System.nanoTime() % 100_000_000L);
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

    /** PED-F003 — operador de cada caixa em lote, para a lista de pedidos. */
    @Test
    void sessionOperatorsAreResolvedInBatch() {
        String[] ctx = givenOpenSessionWithStock();
        Long sessionId = Long.valueOf(ctx[0]);

        Map<Long, String> operators = pdvUseCase.getSessionOperators(List.of(sessionId, -1L));

        assertThat(operators).containsExactly(Map.entry(sessionId, ctx[2]));
    }

    @Test
    void customerPhoneIsNormalized_andLookupMatchesAnyMaskAndEmailCase() {
        String phone = uniquePhone();
        String email = "Maria." + suffix() + "@Example.com";
        String masked = "+55 (" + phone.substring(0, 2) + ") " + phone.substring(2, 7) + "-" + phone.substring(7);
        Customer created = crmUseCase.createCustomer("Maria", masked, email, null, "PDV");

        assertThat(jdbc.queryForObject("SELECT phone_normalized FROM customers WHERE id = ?", String.class,
                created.id())).isEqualTo(phone);

        List<CustomerMatch> matches = crmUseCase.lookupCustomers(phone, email.toLowerCase(), null);
        assertThat(matches).singleElement().satisfies(m -> {
            assertThat(m.customer().id()).isEqualTo(created.id());
            assertThat(m.matchedBy()).containsExactlyInAnyOrder(CustomerMatchField.PHONE, CustomerMatchField.EMAIL);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pg_indexes WHERE indexname IN "
                + "('idx_customers_phone_normalized', 'idx_customers_email_lower')", Integer.class)).isEqualTo(2);
    }

    @Test
    void saleWithEntrega_writesOrderDeliveryRow_andPatchUpdatesIt() {
        String[] ctx = givenOpenSessionWithStock();
        OrderDelivery delivery = new OrderDelivery(DeliveryType.ENTREGA,
                new DeliveryAddress("Rua A", "10", null, "58000-000", "Centro", "João Pessoa", "PB", "Brasil",
                        "Portão azul"),
                DeliveryMethod.APP_99, null, null, null, null, null, new BigDecimal("9.90"));

        Order sold = pdvUseCase.registerSale(Long.valueOf(ctx[0]), null,
                List.of(new SaleItemCommand(ctx[1], new BigDecimal("1.000"), null, "Sem gelo")),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("31.90"), null)), ctx[2], false,
                delivery);

        assertThat(sold.status()).isEqualTo(OrderStatus.RESERVADO);
        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM order_delivery WHERE order_id = ?", sold.id());
        assertThat(row.get("type")).isEqualTo("ENTREGA");
        assertThat(row.get("method")).isEqualTo("APP_99");
        assertThat((BigDecimal) row.get("fee")).isEqualByComparingTo("9.90");
        assertThat(jdbc.queryForObject("SELECT notes FROM order_item WHERE order_id = ?", String.class, sold.id()))
                .isEqualTo("Sem gelo");

        orderUseCase.updateDelivery(sold.id(), new OrderDelivery.Patch(null, null, null, null, null,
                "COL-1", "ENT-2", null, null), ctx[2]);
        Order reloaded = orderUseCase.getOrder(sold.id());
        assertThat(reloaded.delivery().pickupCode()).isEqualTo("COL-1");
        assertThat(reloaded.delivery().dropoffCode()).isEqualTo("ENT-2");
        assertThat(reloaded.delivery().address().reference()).isEqualTo("Portão azul");
        assertThat(reloaded.totalPayable()).isEqualByComparingTo("31.90");
    }

    /** PDV-F025 — V134: canal e operadora gravados, e o CHECK recusa DINHEIRO com canal. */
    @Test
    void paymentChannelAndProvider_roundTripOnPostgres_andCashWithChannelIsRejectedByTheCheck() {
        String[] ctx = givenOpenSessionWithStock();
        Order sold = pdvUseCase.registerSale(Long.valueOf(ctx[0]), null,
                List.of(new SaleItemCommand(ctx[1], new BigDecimal("1.000"), null)),
                List.of(new PaymentCommand(PaymentMethod.CREDITO, new BigDecimal("22.00"), 2,
                        PaymentChannel.MAQUININHA, PaymentProvider.CIELO)), ctx[2]);

        assertThat(orderUseCase.getOrderPayments(sold.id())).singleElement().satisfies(p -> {
            assertThat(p.channel()).isEqualTo(PaymentChannel.MAQUININHA);
            assertThat(p.provider()).isEqualTo(PaymentProvider.CIELO);
        });
        // A linha tem canal: virar DINHEIRO viola ck_order_payment_channel_not_cash.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE order_payment SET method = 'DINHEIRO', installments = NULL WHERE order_id = ?", sold.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saleWithoutDelivery_writesNoOrderDeliveryRow() {
        String[] ctx = givenOpenSessionWithStock();
        Order sold = pdvUseCase.registerSale(Long.valueOf(ctx[0]), null,
                List.of(new SaleItemCommand(ctx[1], new BigDecimal("1.000"), null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("22.00"), null)), ctx[2]);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_delivery WHERE order_id = ?", Integer.class,
                sold.id())).isZero();
        assertThat(orderUseCase.getOrder(sold.id()).delivery()).isNull();
    }

    @Test
    void orderDeliveryChecks_rejectRetiradaWithFeeAndEntregaWithoutAddress() {
        String[] ctx = givenOpenSessionWithStock();
        Order sold = pdvUseCase.registerSale(Long.valueOf(ctx[0]), null,
                List.of(new SaleItemCommand(ctx[1], new BigDecimal("1.000"), null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("22.00"), null)), ctx[2]);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO order_delivery (order_id, type, fee) VALUES (?, 'RETIRADA', 5)", sold.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO order_delivery (order_id, type) VALUES (?, 'ENTREGA')", sold.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
