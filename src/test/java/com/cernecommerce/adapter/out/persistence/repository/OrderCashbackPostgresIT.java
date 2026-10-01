package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.Order;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PED-C005 — pedido já gravado não pode ter os itens regravados. O {@code cashback_entry.order_item_id}
 * aponta para {@code order_item(id)} (V70), e essa FK só existe no Flyway: o H2 do perfil dev monta o
 * schema pelas entidades, onde a coluna é um {@code Long} solto. Por isso a prova é aqui.
 * Habilitar com: {@code ENABLE_TC=true ./mvnw test -Dapi.version=1.44}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class OrderCashbackPostgresIT {

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

    private record Ctx(Long sessionId, String sku, String operator, Long customerId) {}

    private Ctx givenSessionStockAndCustomer() {
        String s = UUID.randomUUID().toString().substring(0, 8);
        String operator = "caixa-" + s;
        estoqueUseCase.createWarehouse("LOJA-" + s, "Loja " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct("ESS-" + s, "Essência " + s, "Essências", List.of(),
                Pricing.of(new BigDecimal("50.00"), null, new BigDecimal("100.00")));
        estoqueUseCase.adjustStock("ESS-" + s, "LOJA-" + s, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga", operator);
        String cpf = String.valueOf(10000000000L + (System.nanoTime() % 89999999999L));
        Customer customer = crmUseCase.createCustomer("Cliente " + s, "1199" + (System.nanoTime() % 10000000L), null, cpf, "balcao");
        CashRegisterSession session = pdvUseCase.openSession(operator, BigDecimal.ZERO, "LOJA-" + s);
        return new Ctx(session.id(), "ESS-" + s, operator, customer.id());
    }

    /** Sobrecarga completa de propósito: os {@code default} da interface perdem a transação (PLAT-C047). */
    private Order sell(Ctx ctx, boolean reserveForPickup) {
        return pdvUseCase.registerSale(ctx.sessionId(), ctx.customerId(),
                List.of(new SaleItemCommand(ctx.sku(), BigDecimal.ONE, null)),
                List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("100.00"), null)),
                ctx.operator(), reserveForPickup, null);
    }

    /** Pré-condição de todo cenário: o EARNED existe e aponta para a linha do pedido. */
    private Long earnedItemId(Long orderId) {
        return jdbc.queryForObject(
                "SELECT order_item_id FROM cashback_entry WHERE order_id = ? AND type = 'EARNED'",
                Long.class, orderId);
    }

    private List<Long> itemIds(Long orderId) {
        return jdbc.queryForList("SELECT id FROM order_item WHERE order_id = ? ORDER BY id", Long.class, orderId);
    }

    @Test
    void refundOfAnOrderWithCashback_keepsTheOrderItemRows() {
        Ctx ctx = givenSessionStockAndCustomer();
        Order sold = sell(ctx, false);
        Long itemId = earnedItemId(sold.id());
        assertThat(itemId).isNotNull();

        Order refunded = orderUseCase.refundOrder(sold.id(), "devolução", ctx.operator());

        assertThat(refunded.status()).isEqualTo(OrderStatus.REEMBOLSADO);
        assertThat(itemIds(sold.id())).containsExactly(itemId);
    }

    @Test
    void pickupOfAReservedOrderWithCashback_keepsTheOrderItemRows() {
        Ctx ctx = givenSessionStockAndCustomer();
        Order reserved = sell(ctx, true);
        assertThat(reserved.status()).isEqualTo(OrderStatus.RESERVADO);
        Long itemId = earnedItemId(reserved.id());
        assertThat(itemId).isNotNull();

        Order picked = orderUseCase.changeStatus(reserved.id(), OrderStatus.CONCLUIDO, ctx.operator());

        assertThat(picked.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(itemIds(reserved.id())).containsExactly(itemId);
    }
}
