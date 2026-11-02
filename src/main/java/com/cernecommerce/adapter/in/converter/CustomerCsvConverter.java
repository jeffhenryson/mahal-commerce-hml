package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.core.domain.model.crm.Customer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Converte clientes para CSV (RFC 4180) — usado pela exportação server-side
 * (`GET /crm/customers/export`, ver crm/exportacao-csv-clientes).
 */
public class CustomerCsvConverter {

    private static final String HEADER = "id,nome,contato,email,cpf,origem,cadastradoEm,estagio,cidade,segmento";
    private static final String LINE_BREAK = "\r\n";

    public String toCsv(List<Customer> customers) {
        String rows = customers.stream().map(this::toRow).collect(Collectors.joining(LINE_BREAK));
        StringBuilder csv = new StringBuilder(HEADER);
        if (!customers.isEmpty()) {
            csv.append(LINE_BREAK).append(rows);
        }
        csv.append(LINE_BREAK);
        return csv.toString();
    }

    private String toRow(Customer c) {
        return String.join(",",
                field(c.id()),
                field(c.nome()),
                field(c.contato()),
                field(c.email()),
                field(c.cpf()),
                field(c.origem()),
                field(c.cadastradoEm()),
                field(c.estagio()),
                field(c.cidade()),
                field(c.segmento()));
    }

    private String field(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        boolean needsQuoting = text.contains(",") || text.contains("\"") || text.contains("\n")
                || text.contains("\r");
        if (!needsQuoting) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
