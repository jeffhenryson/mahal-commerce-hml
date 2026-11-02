package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CustomerNoteResponseDTO;
import com.cernecommerce.core.domain.model.crm.CustomerNote;

public class CustomerNoteDTOConverter {

    public CustomerNoteResponseDTO toResponse(CustomerNote note) {
        CustomerNoteResponseDTO dto = new CustomerNoteResponseDTO();
        dto.setId(note.id());
        dto.setCustomerId(note.customerId());
        dto.setAutor(note.autor());
        dto.setTexto(note.texto());
        dto.setCriadoEm(note.criadoEm());
        dto.setAnexos(note.anexos().stream().map(a -> {
            CustomerNoteResponseDTO.Anexo anexo = new CustomerNoteResponseDTO.Anexo();
            anexo.setId(a.id());
            anexo.setNomeArquivo(a.nomeArquivo());
            anexo.setTipo(a.contentType());
            anexo.setTamanho(a.tamanho());
            anexo.setUrl("/crm/customers/" + note.customerId() + "/notes/" + note.id() + "/anexos/" + a.id());
            return anexo;
        }).toList());
        return dto;
    }
}
