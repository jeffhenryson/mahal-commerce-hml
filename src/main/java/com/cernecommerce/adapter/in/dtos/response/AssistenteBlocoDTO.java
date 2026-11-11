package com.cernecommerce.adapter.in.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Bloco montado pelo servidor a partir do resultado de uma ferramenta, com valores já formatados
 * (pt-BR). O front descarta tipos que não conhece, então os campos de cada tipo são opcionais:
 * <ul>
 *   <li>{@code tabela}: {@code colunas} + {@code linhas};</li>
 *   <li>{@code kpi}: {@code itens} (cartões de número);</li>
 *   <li>{@code grafico}: {@code forma} (linha|barra), {@code rotuloX}, {@code rotuloY}, {@code sensivel}
 *       e {@code pontos} (no máximo {@value #MAX_PONTOS}; {@code y} é o único valor numérico).</li>
 * </ul>
 * {@code sensivel} segue a regra das colunas: o valor some com o "olho fechado".
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistenteBlocoDTO(String tipo, String titulo, List<AssistenteColunaDTO> colunas,
        List<List<String>> linhas, List<KpiItem> itens, String forma, String rotuloX, String rotuloY,
        Boolean sensivel, List<Ponto> pontos) {

    public static final int MAX_PONTOS = 31;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KpiItem(String rotulo, String valor, String variacao, String tendencia, boolean sensivel) { }

    public record Ponto(String x, double y) { }

    public static AssistenteBlocoDTO tabela(String titulo, List<AssistenteColunaDTO> colunas, List<List<String>> linhas) {
        return new AssistenteBlocoDTO("tabela", titulo, colunas, linhas, null, null, null, null, null, null);
    }

    public static AssistenteBlocoDTO kpi(String titulo, List<KpiItem> itens) {
        return new AssistenteBlocoDTO("kpi", titulo, null, null, List.copyOf(itens), null, null, null, null, null);
    }

    /** Mais de {@value #MAX_PONTOS} pontos: ficam os últimos (a parte mais recente da série). */
    public static AssistenteBlocoDTO grafico(String titulo, String forma, String rotuloX, String rotuloY,
            boolean sensivel, List<Ponto> pontos) {
        List<Ponto> serie = pontos.size() > MAX_PONTOS ? pontos.subList(pontos.size() - MAX_PONTOS, pontos.size())
                : pontos;
        return new AssistenteBlocoDTO("grafico", titulo, null, null, null, forma, rotuloX, rotuloY, sensivel,
                List.copyOf(serie));
    }
}
