package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerFilter;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.ports.out.crm.CustomerInsightsRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.crm.TagRepository;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pendências 7 e 8 contra Postgres real (V183): filtros da lista em Specification (unaccent, subquery
 * de tag, ordem), números em lote e o recálculo de segmento. Habilitar com {@code ENABLE_TC=true}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class CustomerListingPostgresIT {

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

    @Autowired CustomerRepository customerRepository;
    @Autowired CustomerInsightsRepository insights;
    @Autowired TagRepository tagRepository;
    @Autowired CustomerTagRepository customerTagRepository;
    @Autowired JdbcTemplate jdbc;

    private Customer novo(String nome, String cidade, String origem) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return customerRepository.save(Customer.create(nome, null, tag + "@teste.com", null, origem)
                .withCidadeEAvatar(cidade, null));
    }

    private void pedidoPago(Long customerId, String valor, Instant quando) {
        jdbc.update("""
                INSERT INTO sales_order (order_number, channel, status, customer_id, warehouse_code, total_amount,
                    discount_amount, cashback_redeemed, net_amount, sold_at, version)
                VALUES (?, 'MARKETPLACE', 'ENTREGUE', ?, 'LOJA-01', ?, 0, 0, ?, ?, 0)
                """, UUID.randomUUID().toString().substring(0, 12), customerId, new BigDecimal(valor),
                new BigDecimal(valor), Timestamp.from(quando));
    }

    @Test
    void filtrosOrdemENumerosEmLote() {
        String sufixo = UUID.randomUUID().toString().substring(0, 6);
        Customer joao = novo("João Filtro " + sufixo, "Fortaleza", "Instagram");
        Customer bia = novo("Bia Filtro " + sufixo, "Recife", "Balcão");
        Customer caio = novo("Caio Filtro " + sufixo, "Fortaleza", "instagram");
        Tag vip = tagRepository.findByNome("VIP").orElseGet(() -> tagRepository.save(Tag.create("VIP")));
        customerTagRepository.associate(bia.id(), vip.id());
        Instant agora = Instant.now();
        for (int i = 0; i < 3; i++) {
            pedidoPago(joao.id(), "100.00", agora.minus(Duration.ofDays(2 + i)));
        }
        pedidoPago(caio.id(), "50.00", agora.minus(Duration.ofDays(60)));

        // Busca sem acento + origem sem caixa + ordem por nome ASC.
        PageResult<Customer> page = customerRepository.findAll(new CustomerFilter("joao filtro " + sufixo, null, null,
                List.of("INSTAGRAM"), null, "nome", true), 0, 20);
        assertThat(page.content()).extracting(Customer::id).containsExactly(joao.id());

        PageResult<Customer> instagram = customerRepository.findAll(new CustomerFilter("filtro " + sufixo, null, null,
                List.of("Instagram"), null, "nome", true), 0, 20);
        assertThat(instagram.content()).extracting(Customer::id).containsExactly(caio.id(), joao.id());
        assertThat(instagram.totalElements()).isEqualTo(2);

        // Tag (subquery) e estágio.
        PageResult<Customer> comTag = customerRepository.findAll(new CustomerFilter("filtro " + sufixo,
                List.of(CustomerStage.NOVO_LEAD), null, null, List.of(vip.id()), null, null), 0, 20);
        assertThat(comTag.content()).extracting(Customer::id).containsExactly(bia.id());

        // Números em lote.
        Map<Long, CustomerInsightsRepository.CustomerStats> stats = insights.statsFor(List.of(joao.id(), bia.id(),
                caio.id()));
        assertThat(stats.get(joao.id()).ltv()).isEqualByComparingTo("300.00");
        assertThat(stats.get(joao.id()).pedidos()).isEqualTo(3);
        assertThat(stats).doesNotContainKey(bia.id());
        assertThat(insights.tagNamesFor(List.of(bia.id())).get(bia.id())).containsExactly("VIP");
        assertThat(insights.cashbackAvailableFor(List.of(joao.id()), agora)).isEmpty();
        assertThat(insights.whatsappDispatchesBetween(agora.minus(Duration.ofDays(30)), agora)).isZero();
        assertThat(insights.averageLtv()).isPositive();

        // Segmento: Recorrente (3 pedidos recentes), VIP (tag), Em Risco (última há 60 dias).
        insights.recalculateSegments(null, agora);
        assertThat(customerRepository.findById(joao.id()).orElseThrow().segmento()).isEqualTo("Recorrente");
        assertThat(customerRepository.findById(bia.id()).orElseThrow().segmento()).isEqualTo("VIP");
        assertThat(customerRepository.findById(caio.id()).orElseThrow().segmento()).isEqualTo("Em Risco");
        assertThat(insights.countBySegmento()).containsKeys("Recorrente", "VIP", "Em Risco");

        PageResult<Customer> emRisco = customerRepository.findAll(new CustomerFilter("filtro " + sufixo, null,
                List.of("Em Risco", "VIP"), null, null, "nome", true), 0, 20);
        assertThat(emRisco.content()).extracting(Customer::id).containsExactly(bia.id(), caio.id());

        // Segmento manual não é sobrescrito.
        customerRepository.save(customerRepository.findById(caio.id()).orElseThrow().withSegmentoManual("VIP"));
        insights.recalculateSegments(caio.id(), agora);
        Customer manual = customerRepository.findById(caio.id()).orElseThrow();
        assertThat(manual.segmento()).isEqualTo("VIP");
        assertThat(manual.segmentoManual()).isTrue();
        assertThat(manual.cidade()).isEqualTo("Fortaleza");
    }
}
