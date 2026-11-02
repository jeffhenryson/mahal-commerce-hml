package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.time.Instant;

@Data
public class CustomerNoteResponseDTO {
    private Long id;
    private Long customerId;
    private String autor;
    private String texto;
    private Instant criadoEm;

    @io.swagger.v3.oas.annotations.media.Schema(description = "V183 — anexos da nota. Baixe por `url` (exige "
            + "CRM_CUSTOMER_READ; não é link público).")
    private java.util.List<Anexo> anexos = java.util.List.of();

    @Data
    public static class Anexo {
        private Long id;
        private String nomeArquivo;
        private String tipo;
        private long tamanho;
        @io.swagger.v3.oas.annotations.media.Schema(description = "Caminho da API para baixar o arquivo.",
                example = "/crm/customers/1/notes/10/anexos/3")
        private String url;
    }
}
