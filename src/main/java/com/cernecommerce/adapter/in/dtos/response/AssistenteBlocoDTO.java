package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;

/** Tabela montada pelo servidor a partir do resultado de uma ferramenta, com valores já formatados. */
public record AssistenteBlocoDTO(String tipo, String titulo, List<AssistenteColunaDTO> colunas, List<List<String>> linhas) {

    public static AssistenteBlocoDTO tabela(String titulo, List<AssistenteColunaDTO> colunas, List<List<String>> linhas) {
        return new AssistenteBlocoDTO("tabela", titulo, colunas, linhas);
    }
}
