package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerCsvConverterTest {

    private final CustomerCsvConverter converter = new CustomerCsvConverter();

    private Customer customer(String nome, String contato, String email, String cpf, String origem,
            CustomerStage estagio) {
        return Customer.of(1L, nome, contato, email, cpf, origem, Instant.parse("2026-07-21T12:00:00Z"), estagio);
    }

    @Test
    void toCsv_includesHeaderRow() {
        String csv = converter.toCsv(List.of());

        assertThat(csv).startsWith("id,nome,contato,email,cpf,origem,cadastradoEm,estagio,cidade,segmento\r\n");
    }

    @Test
    void toCsv_includesOneLinePerCustomer() {
        Customer c1 = customer("Maria Silva", "11999998888", "maria@example.com", "12345678900", "loja-fisica",
                CustomerStage.NOVO_LEAD);
        Customer c2 = customer("Joao Souza", "11988887777", "joao@example.com", null, null,
                CustomerStage.CLIENTE_ATIVO);

        String csv = converter.toCsv(List.of(c1, c2));

        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(3); // header + 2 clientes
        assertThat(lines[1]).contains("Maria Silva", "11999998888", "maria@example.com", "NOVO_LEAD");
        assertThat(lines[2]).contains("Joao Souza", "CLIENTE_ATIVO");
    }

    @Test
    void toCsv_quotesFieldsContainingComma() {
        Customer c = customer("Silva, Maria", "11999998888", "maria@example.com", null, null,
                CustomerStage.NOVO_LEAD);

        String csv = converter.toCsv(List.of(c));

        assertThat(csv).contains("\"Silva, Maria\"");
    }

    @Test
    void toCsv_escapesInternalQuotesByDoublingThem() {
        Customer c = customer("Cliente \"VIP\"", "11999998888", "maria@example.com", null, null,
                CustomerStage.NOVO_LEAD);

        String csv = converter.toCsv(List.of(c));

        assertThat(csv).contains("\"Cliente \"\"VIP\"\"\"");
    }

    @Test
    void toCsv_leavesNullOptionalFieldsBlank() {
        Customer c = customer("Maria Silva", "11999998888", "maria@example.com", null, null,
                CustomerStage.NOVO_LEAD);

        String csv = converter.toCsv(List.of(c));

        String dataLine = csv.split("\r\n")[1];
        assertThat(dataLine).isEqualTo("1,Maria Silva,11999998888,maria@example.com,,,2026-07-21T12:00:00Z,NOVO_LEAD,,Novo");
    }
}
