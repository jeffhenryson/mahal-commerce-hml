package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CustomerMatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CustomerResponseDTO;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;

import java.math.BigDecimal;
import java.util.List;

public class CustomerDTOConverter {

    // Placeholder até os domínios de pedidos e cashback existirem — ver crm/listagem-clientes-rfm.
    private static final BigDecimal LTV_PLACEHOLDER = BigDecimal.ZERO;
    private static final BigDecimal CASHBACK_PLACEHOLDER = BigDecimal.ZERO;
    private static final String SEGMENTO_PLACEHOLDER = "NOVO";

    /**
     * Converte sem buscar as tags reais — usado para clientes recém-criados (sem tags ainda)
     * e para a listagem paginada (evita N+1: buscar tags por cliente a cada linha da página).
     */
    public CustomerResponseDTO toResponse(Customer customer) {
        return toResponse(customer, List.of());
    }

    /** Item do lookup por contato (CRM-C007): o cliente mais os campos que bateram. */
    public CustomerMatchResponseDTO toMatchResponse(CustomerMatch match) {
        CustomerMatchResponseDTO dto = fill(new CustomerMatchResponseDTO(), match.customer(), List.of());
        dto.setMatchedBy(match.matchedBy().stream().sorted().toList());
        return dto;
    }

    /** Converte com a lista real de nomes de tags associadas ao cliente. */
    public CustomerResponseDTO toResponse(Customer customer, List<String> tagNomes) {
        return fill(new CustomerResponseDTO(), customer, tagNomes);
    }

    private <T extends CustomerResponseDTO> T fill(T dto, Customer customer, List<String> tagNomes) {
        dto.setId(customer.id());
        dto.setNome(customer.nome());
        dto.setContato(customer.contato());
        dto.setEmail(customer.email());
        dto.setCpf(customer.cpf());
        dto.setOrigem(customer.origem());
        dto.setCadastradoEm(customer.cadastradoEm());
        dto.setEstagio(customer.estagio());
        dto.setDataNascimento(customer.dataNascimento());
        dto.setLtv(LTV_PLACEHOLDER);
        dto.setCashback(CASHBACK_PLACEHOLDER);
        dto.setSegmento(SEGMENTO_PLACEHOLDER);
        dto.setTags(tagNomes);
        return dto;
    }
}
