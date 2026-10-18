package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Repor essência" contra banco: a lata velha fecha e a nova abre na mesma transação sem colidir no
 * índice parcial da V124. Antes o INSERT da nova (IDENTITY) saía antes do UPDATE da velha e o repor
 * respondia 409 {@code OPEN_PACKAGE_ALREADY_OPEN} justamente quando havia lata para trocar. O índice é
 * parcial e só existe no Flyway — no H2 o teste passaria com o bug. Habilitar com:
 * {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
@Transactional
class OpenPackageReplacePostgresIT {

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

    @Autowired EstoqueUseCase estoqueUseCase;

    @Test
    void reporComLataAberta_fechaAVelha_abreANova_eBaixaUmaLata() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sku = "ZIGY-BANANA-" + s;
        String warehouse = "LOUNGE-" + s;
        String operador = "atendente-" + s;
        estoqueUseCase.createWarehouse(warehouse, "Lounge " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Zigy Banana " + s, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 5, null));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("3"), "carga", operador);

        // O caso relatado: rende ~5 e já foi usada em 12 sessões.
        for (int i = 0; i < 12; i++) {
            estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        }
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("2");

        OpenPackage nova = estoqueUseCase.replaceOpenPackage(sku, warehouse, operador);

        assertThat(nova.isOpen()).isTrue();
        assertThat(nova.uses()).isZero();
        assertThat(estoqueUseCase.listOpenPackages(warehouse)).singleElement()
                .satisfies(l -> assertThat(l.id()).isEqualTo(nova.id()));
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("1");
    }

    @Test
    void acabouEDepoisAbrirLata_baixaUmaLata() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sku = "ZIGY-MENTA-" + s;
        String warehouse = "LOUNGE-" + s;
        String operador = "atendente-" + s;
        estoqueUseCase.createWarehouse(warehouse, "Lounge " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Zigy Menta " + s, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 5, null));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("3"), "carga", operador);

        estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        estoqueUseCase.finishOpenPackage(sku, warehouse, operador);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("2");

        estoqueUseCase.replaceOpenPackage(sku, warehouse, operador);

        assertThat(estoqueUseCase.listOpenPackages(warehouse)).hasSize(1);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("1");
    }
}
