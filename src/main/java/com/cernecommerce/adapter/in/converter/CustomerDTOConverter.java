package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CustomerMatchResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CustomerResponseDTO;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerMatch;

import java.util.List;

public class CustomerDTOConverter {

    /**
     * Converte sem tags nem números de outros domínios — usado nas respostas de escrita (criar,
     * atualizar), em que ltv/cashback saem nulos em vez de um zero que fingiria ser dado real.
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
        dto.setSegmento(customer.segmento());
        dto.setSegmentoManual(customer.segmentoManual());
        dto.setCidade(customer.cidade());
        dto.setAvatarUrl(customer.avatarUrl());
        dto.setTags(tagNomes);
        return dto;
    }

    /** Pendência 8 — o cliente com LTV, pedidos, cashback e tags reais. */
    public CustomerResponseDTO toResponse(com.cernecommerce.core.domain.model.crm.CustomerSummary summary) {
        CustomerResponseDTO dto = fill(new CustomerResponseDTO(), summary.customer(), summary.tags());
        dto.setLtv(summary.ltv());
        dto.setCashback(summary.cashback());
        dto.setPedidosCount(summary.ltv() == null ? null : summary.pedidosCount());
        dto.setTicketMedio(summary.ticketMedio());
        dto.setUltimaCompraEm(summary.ultimaCompraEm());
        return dto;
    }
}
