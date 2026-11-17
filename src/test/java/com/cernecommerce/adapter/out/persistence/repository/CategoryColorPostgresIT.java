package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.estoque.Category;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V205 — cor da categoria no PDV, contra um Postgres real via Testcontainers.
 * Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 *
 * <p>O H2 dos outros testes cria a tabela a partir da entidade e não conhece o CHECK da migration;
 * só o Postgres prova que a constraint recusa uma cor fora de {@code #RRGGBB} em maiúsculo
 * gravada direto no banco, e que o round-trip pelo repositório preserva a cor.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class CategoryColorPostgresIT {

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
        // Mesmo motivo do ProductRepositoryPostgresIT: o dev-schema.sql é sintaxe de H2.
        r.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired CategoryRepositoryImpl categoryRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void roundTrip_preservaACor_eANulaContinuaNula() {
        Category comCor = categoryRepository.save(
                Category.create("Cor IT Essências", false, 0, false, false, "#f5b638"));
        Category semCor = categoryRepository.save(Category.create("Cor IT Narguilé"));

        assertThat(categoryRepository.findById(comCor.id()).orElseThrow().color()).isEqualTo("#F5B638");
        assertThat(categoryRepository.findById(semCor.id()).orElseThrow().color()).isNull();
    }

    @Test
    void constraint_recusaCorForaDoPadrao_gravadaDiretoNoBanco() {
        Category categoria = categoryRepository.save(Category.create("Cor IT Constraint"));

        for (String cor : new String[] {"#f5b638", "#12", "vermelho", "#GGGGGG"}) {
            assertThatThrownBy(() -> jdbc.update("UPDATE product_category SET color = ? WHERE id = ?",
                    cor, categoria.id()))
                    .as(cor)
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        jdbc.update("UPDATE product_category SET color = ? WHERE id = ?", "#F5B638", categoria.id());
        assertThat(categoryRepository.findById(categoria.id()).orElseThrow().color()).isEqualTo("#F5B638");
    }
}
