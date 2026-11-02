package com.cernecommerce.core.domain.model.crm;

import java.util.List;
import java.util.Set;

/**
 * Filtro e ordem da lista de clientes (pendência 7 do admin). Dentro do mesmo campo os valores são
 * OU ({@code estagio=NOVO_LEAD&estagio=QUALIFICADO}); entre campos, E. Lista vazia não filtra.
 *
 * @param sort {@code nome}, {@code email}, {@code cadastradoEm} ou {@code estagio}; nulo vale
 *        {@code cadastradoEm}
 * @param ascending nulo vale {@code false} (mais recente primeiro, como sempre foi)
 */
public record CustomerFilter(String search, List<CustomerStage> estagios, List<String> segmentos,
        List<String> origens, List<Long> tagIds, String sort, Boolean ascending) {

    public static final Set<String> SORTABLE = Set.of("nome", "email", "cadastradoEm", "estagio");
    public static final String DEFAULT_SORT = "cadastradoEm";

    public CustomerFilter {
        search = search == null || search.isBlank() ? null : search.trim();
        estagios = estagios == null ? List.of() : List.copyOf(estagios);
        segmentos = segmentos == null ? List.of() : segmentos.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim).toList();
        origens = origens == null ? List.of() : origens.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim).toList();
        tagIds = tagIds == null ? List.of() : List.copyOf(tagIds);
        if (sort != null && !sort.isBlank() && !SORTABLE.contains(sort)) {
            throw new IllegalArgumentException("sort inválido: " + sort + " (use " + SORTABLE + ")");
        }
        sort = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
        ascending = ascending != null && ascending;
        for (String segmento : segmentos) {
            if (!Customer.SEGMENTOS.contains(segmento)) {
                throw new IllegalArgumentException("segmento inválido: " + segmento);
            }
        }
    }

    /** Só a busca livre, na ordem padrão: o comportamento anterior à pendência 7. */
    public static CustomerFilter searchOnly(String search) {
        return new CustomerFilter(search, null, null, null, null, null, null);
    }
}
