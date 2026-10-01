package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRM-C008 contra Postgres real: a busca por nome manda {@code cpfDigits} nulo, e um parâmetro
 * nulo testado com {@code IS NOT NULL} em JPQL não tem tipo no Postgres ("could not determine data
 * type of parameter") — a busca de cliente do PDV caía em 500. O H2 aceita, por isso só aparece aqui.
 * Habilitar com: {@code ENABLE_TC=true ./mvnw test}
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
@EnabledIfEnvironmentVariable(named = "ENABLE_TC", matches = "true")
class CustomerSearchPostgresIT {

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

    private Customer novo(String nome, String cpf) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        return customerRepository.save(Customer.create(nome, null, tag + "@teste.com", cpf, "PDV"));
    }

    @Test
    void searchByName_withoutDigits_works() {
        Customer joana = novo("Joana Busca Nome", null);

        PageResult<Customer> page = customerRepository.findAll("busca nome", 0, 20);

        assertThat(page.content()).extracting(Customer::id).contains(joana.id());
    }

    @Test
    void searchByCpfDigits_works() {
        Customer carlos = novo("Carlos Busca Cpf", "52998224725");

        PageResult<Customer> page = customerRepository.findAll("529.982", 0, 20);

        assertThat(page.content()).extracting(Customer::id).contains(carlos.id());
    }

    @Test
    void exportSearchByName_withoutDigits_works() {
        Customer bia = novo("Bia Exporta Nome", null);

        List<Customer> all = customerRepository.findAllForExport("exporta nome");

        assertThat(all).extracting(Customer::id).contains(bia.id());
    }
}
