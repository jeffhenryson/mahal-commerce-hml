package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.UsoTokens;
import com.cernecommerce.core.ports.out.assistente.AssistenteConsultasQuery;
import com.cernecommerce.core.ports.out.assistente.AssistenteUsoPort;
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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assistente, rodada 3, contra Postgres real com o Flyway inteiro: a V201 aplica e o SQL das
 * consultas novas (uso, feedback, categorias, ruptura, clientes inativos) roda no Postgres, não só
 * no H2. Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class AssistenteConsultasPostgresIT {

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

    @Autowired AssistenteUsoPort uso;
    @Autowired AssistenteConsultasQuery consultas;
    @Autowired JdbcTemplate jdbc;

    @Test
    void uso_e_feedback_sobre_a_v201() {
        Instant agora = Instant.now();
        String conversa = UUID.randomUUID().toString();
        String pergunta = UUID.randomUUID().toString();
        String resposta = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO assistente_conversa (id, username, titulo, criada_em, atualizada_em) VALUES (?, 'pg', 't', ?, ?)",
                conversa, Timestamp.from(agora), Timestamp.from(agora));
        jdbc.update("INSERT INTO assistente_mensagem (id, conversa_id, papel, texto, criada_em) VALUES (?, ?, 'USUARIO', 'quanto vendi?', ?)",
                pergunta, conversa, Timestamp.from(agora.minusSeconds(5)));
        jdbc.update("INSERT INTO assistente_mensagem (id, conversa_id, papel, texto, provedor, criada_em, feedback_nota,"
                + " feedback_motivo, feedback_em) VALUES (?, ?, 'ASSISTENTE', 'R$ 10', 'ANTHROPIC', ?, 'NEGATIVO', 'curto', ?)",
                resposta, conversa, Timestamp.from(agora), Timestamp.from(agora));

        uso.registrar(resposta, new UsoTokens(100, 20, 7));

        Instant de = agora.minus(Duration.ofDays(1));
        Instant ate = agora.plus(Duration.ofDays(1));
        assertThat(uso.tokensNoLimite(de, ate)).isEqualTo(120);
        assertThat(uso.usoPorUsuario(de, ate)).singleElement().satisfies(l -> {
            assertThat(l.username()).isEqualTo("pg");
            assertThat(l.uso()).isEqualTo(new UsoTokens(100, 20, 7));
        });
        assertThat(uso.feedbacks(NotaFeedback.NEGATIVO, 10)).singleElement().satisfies(f -> {
            assertThat(f.pergunta()).isEqualTo("quanto vendi?");
            assertThat(f.resposta()).isEqualTo("R$ 10");
            assertThat(f.motivo()).isEqualTo("curto");
        });
        assertThat(uso.feedbacks(null, 10)).hasSize(1);
    }

    @Test
    void consultas_de_analise_rodam_no_postgres() {
        Instant agora = Instant.now();
        jdbc.update("INSERT INTO warehouse (code, name, type, active) VALUES ('PG-AST', 'Depósito PG', 'LOJA_FISICA', TRUE)");
        Long deposito = jdbc.queryForObject("SELECT id FROM warehouse WHERE code = 'PG-AST'", Long.class);
        jdbc.update("INSERT INTO stock_balance (sku, warehouse_id, quantity, reserved_quantity, version) VALUES ('PG-SKU', ?, 4, 1, 0)",
                deposito);
        jdbc.update("INSERT INTO stock_movement (sku, warehouse_id, type, quantity, reason, username, created_at)"
                + " VALUES ('PG-SKU', ?, 'SAIDA', 10, 'venda', 'pg', ?)", deposito, Timestamp.from(agora.minusSeconds(3600)));

        assertThat(consultas.saidasDesde(agora.minus(Duration.ofDays(30)))).singleElement().satisfies(s -> {
            assertThat(s.sku()).isEqualTo("PG-SKU");
            assertThat(s.disponivel()).isEqualByComparingTo("3");
            assertThat(s.saidaNaJanela()).isEqualByComparingTo("10");
        });
        assertThat(consultas.topCategorias(agora.minus(Duration.ofDays(7)), agora, 10, false)).isEmpty();
        assertThat(consultas.topCategorias(agora.minus(Duration.ofDays(7)), agora, 10, true)).isEmpty();
        assertThat(consultas.clientesInativos(agora, 10)).isEmpty();
    }
}
