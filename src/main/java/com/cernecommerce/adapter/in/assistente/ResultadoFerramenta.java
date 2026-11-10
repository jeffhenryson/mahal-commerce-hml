package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;

import java.util.List;

/**
 * O que uma ferramenta devolve: o JSON que vai ao modelo (já sem dados pessoais) e o que o
 * servidor mostra direto na tela — tabelas, links e, nas escritas, a ação a confirmar.
 */
public record ResultadoFerramenta(String paraModelo, boolean erro, List<AssistenteBlocoDTO> blocos,
        List<AssistenteLinkDTO> links, AcaoProposta acao) {

    public static ResultadoFerramenta ok(String paraModelo) {
        return new ResultadoFerramenta(paraModelo, false, List.of(), List.of(), null);
    }

    public static ResultadoFerramenta falha(String mensagem) {
        return new ResultadoFerramenta(mensagem, true, List.of(), List.of(), null);
    }

    public static ResultadoFerramenta proposta(AcaoProposta acao) {
        return new ResultadoFerramenta(null, false, List.of(), List.of(), acao);
    }

    public ResultadoFerramenta comBloco(AssistenteBlocoDTO bloco) {
        return new ResultadoFerramenta(paraModelo, erro, juntar(blocos, bloco), links, acao);
    }

    public ResultadoFerramenta comLink(AssistenteLinkDTO link) {
        return new ResultadoFerramenta(paraModelo, erro, blocos, juntar(links, link), acao);
    }

    private static <T> List<T> juntar(List<T> lista, T item) {
        return java.util.stream.Stream.concat(lista.stream(), java.util.stream.Stream.of(item)).toList();
    }
}
