package com.cernecommerce.core.domain.model.estoque;

/**
 * Critérios opcionais de listagem do catálogo. Todo campo nulo significa "não filtra".
 *
 * <p>Existe porque a listagem só aceitava {@code page}/{@code size}: o admin era obrigado a
 * baixar o catálogo inteiro para filtrar no cliente, e como o teto de {@code size} é 100
 * (EST-C005), qualquer catálogo maior que isso fazia busca, KPI e exportação passarem a mentir em
 * silêncio — sem erro e sem aviso, porque os 100 primeiros sempre voltam.</p>
 *
 * <p>O construtor normaliza: texto em branco vira nulo (para {@code ?search=} não filtrar por
 * string vazia) e {@code search} é guardado já em minúsculas, porque a comparação é
 * case-insensitive e fazê-lo aqui evita repetir a normalização em cada adapter.</p>
 *
 * <p>{@code archived} é a exceção ao "nulo não filtra" (EST-F054): nulo ou falso <b>exclui</b>
 * os arquivados, verdadeiro devolve <b>só</b> eles. Produto excluído não pode reaparecer em
 * nenhuma listagem que esqueça de pedir para escondê-lo.</p>
 */
public record ProductFilter(String search, String category, String brand, Boolean active, ProductType type,
        Boolean kitComponentEligible, ProductStatus status, Boolean sessionProduct, Boolean archived) {

    public static final ProductFilter EMPTY = new ProductFilter(null, null, null, null, null, null, null, null, null);

    public ProductFilter {
        search = normalize(search);
        category = normalize(category);
        brand = normalize(brand);
    }

    /** Mesma forma, sem filtro de {@code type} — compatibilidade com chamadores anteriores a EST-F022. */
    public ProductFilter(String search, String category, String brand, Boolean active) {
        this(search, category, brand, active, null, null, null, null);
    }

    /**
     * Mesma forma, sem filtro de {@code kitComponentEligible} — compatibilidade com chamadores
     * anteriores ao Bloco 2.4.
     */
    public ProductFilter(String search, String category, String brand, Boolean active, ProductType type) {
        this(search, category, brand, active, type, null, null, null);
    }

    /** Mesma forma, sem filtro de {@code status} — compatibilidade com chamadores anteriores a EST-F023. */
    public ProductFilter(String search, String category, String brand, Boolean active, ProductType type,
            Boolean kitComponentEligible) {
        this(search, category, brand, active, type, kitComponentEligible, null, null);
    }

    /**
     * Mesma forma, sem filtro de {@code sessionProduct} — compatibilidade com chamadores anteriores
     * à paginação de Salão › Essências, que só precisa das essências de sessão.
     */
    public ProductFilter(String search, String category, String brand, Boolean active, ProductType type,
            Boolean kitComponentEligible, ProductStatus status) {
        this(search, category, brand, active, type, kitComponentEligible, status, null, null);
    }

    /** Mesma forma, sem {@code archived} — exclui os arquivados, como todo chamador anterior a EST-F054. */
    public ProductFilter(String search, String category, String brand, Boolean active, ProductType type,
            Boolean kitComponentEligible, ProductStatus status, Boolean sessionProduct) {
        this(search, category, brand, active, type, kitComponentEligible, status, sessionProduct, null);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    public boolean isEmpty() {
        return search == null && category == null && brand == null && active == null && type == null
                && kitComponentEligible == null && status == null && sessionProduct == null && archived == null;
    }
}
