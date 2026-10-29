package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.CategoryProductCount;
import com.cernecommerce.core.domain.model.estoque.EstoqueSummary;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port de saída para persistência de produtos do estoque.
 */
public interface ProductRepository {

    /**
     * Listagem sem filtro, ordenada por id. Mantida como {@code default} sobre a assinatura
     * filtrada para os chamadores antigos não mudarem — mesmo idioma de evolução usado em
     * {@code EstoqueUseCase}.
     */
    default PageResult<Product> findAll(int page, int size) {
        return findAll(page, size, ProductFilter.EMPTY, ProductSortField.ID, SortDirection.ASC);
    }

    /**
     * Listagem filtrada e ordenada do catálogo.
     *
     * <p>A ordenação sempre desempata por {@code id}, mesmo quando o campo pedido é outro: nome e
     * preço não são chaves únicas, e paginar por chave não-única faz o banco devolver a mesma
     * linha em duas páginas ou em nenhuma. É a mesma lição de EST-C012, que apareceu no ledger de
     * movimentações e vale igual aqui.</p>
     */
    PageResult<Product> findAll(int page, int size, ProductFilter filter,
            ProductSortField sortField, SortDirection direction);

    /**
     * Só produto ativo e precificado, paginado — a base do catálogo público (ECM-F002). Filtrado
     * na consulta, não em memória sobre {@link #findAll}, para a página e o total baterem certo.
     *
     * @param onSale filtro opcional de promoção (Estágio 01 do admin) — {@code null} não filtra;
     *        {@code true}/{@code false} restringe à sinalização exata.
     */
    default PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale) {
        return findAllActiveAndPriced(page, size, onSale, null);
    }

    /**
     * @param categoryId filtro opcional de categoria — {@code null} não filtra. A ordenação é
     *        sempre a da vitrine: categoria em destaque primeiro, depois ordem de exibição,
     *        depois id.
     */
    default PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale, Long categoryId) {
        return findAllActiveAndPriced(page, size, onSale, categoryId, null);
    }

    /**
     * @param search busca livre opcional (EST-F029) — nome, SKU, categoria ou marca, sem caixa nem
     *        acento; {@code null} ou em branco não filtra.
     */
    PageResult<Product> findAllActiveAndPriced(int page, int size, Boolean onSale, Long categoryId, String search);

    /** ECM-F013 — {@link #findAllActiveAndPriced}, só com o que está visível no app. */
    PageResult<Product> findMarketplaceCatalog(int page, int size, Boolean onSale, Long categoryId, String search);

    /** ECM-F013 — os SKUs da lista que existem. */
    List<String> findExistingSkus(java.util.Collection<String> skus);

    /** ECM-F013 — liga ou desliga no app em lote; devolve quantos produtos mudaram. */
    int updateVisibleInMarketplace(java.util.Collection<String> skus, boolean visible);

    Optional<Product> findBySku(String sku);

    /**
     * Busca o produto pai a partir de <b>qualquer</b> SKU do catálogo — o do próprio pai ou o de
     * uma variação. Distinto de {@link #findBySku(String)}, que só encontra pelo SKU pai.
     *
     * <p>É o caminho de resolução de preço (EST-F019): o PDV lê o código da variação na
     * prateleira, mas a {@code Pricing} mora no pai.</p>
     */
    Optional<Product> findByAnySku(String sku);

    /** PDV-C059 — {@link #findByAnySku} em lote. SKU sem produto não traz nada; cada produto vem uma vez. */
    List<Product> findByAnySkuIn(Collection<String> skus);

    /**
     * Indica se o SKU existe no catálogo, seja como SKU pai de um produto ou como SKU de uma
     * variação. É a checagem usada antes de movimentar saldo, para impedir que uma digitação
     * errada crie saldo e ledger órfãos.
     */
    boolean existsBySku(String sku);

    /**
     * EST-F030 — reescreve {@code currentSku} para {@code newSku} em <b>todas</b> as tabelas que
     * guardam SKU como texto (catálogo, saldo, lotes, movimentos, reservas, carrinho, comanda,
     * pedidos, compras, NF-e, receitas de kit), numa única transação. Não há FK para cascatear:
     * o SKU sempre foi referenciado como texto livre, e é por isso que a troca mora aqui e não
     * num {@code save}.
     *
     * <p>Não valida nada — existência do atual e unicidade do novo são do chamador. Depois de
     * chamar, entidades já carregadas na sessão estão obsoletas; a implementação limpa o contexto
     * de persistência.</p>
     *
     * @return total de linhas reescritas, somando todas as tabelas
     */
    int renameSku(String currentSku, String newSku);

    /**
     * Indica se o SKU está <b>ativo</b> e, portanto, pode receber entrada de estoque (EST-F018).
     * SKU de variação exige que a variação e o produto pai estejam ativos.
     *
     * <p>Distinto de {@link #existsBySku(String)}: um SKU desativado continua existindo — o
     * histórico e o saldo dele seguem válidos, e a saída continua permitida.</p>
     */
    boolean isSkuActive(String sku);

    /**
     * Indica se o código de barras existe no catálogo, seja no produto pai ou em alguma variação.
     * Mesma checagem de {@link #existsBySku(String)}, para o campo aditivo de EAN/GTIN.
     */
    boolean existsByBarcode(String barcode);

    /**
     * Busca o produto pai a partir do código de barras do próprio pai ou de qualquer variação —
     * o caminho de resolução usado pelo scanner do PDV.
     */
    Optional<Product> findByBarcode(String barcode);

    Product save(Product product);

    /**
     * Quantidade de SKUs pai publicados ({@code status = ATIVO}), ativos ou não — rascunho e
     * arquivado ficam fora (EST-F054). Ver {@link EstoqueSummary#totalProdutos}.
     */
    long countProducts();

    /** Quantidade de variações (SKU filho) em todo o catálogo — ver {@link EstoqueSummary#totalVariantes}. */
    long countVariants();

    /**
     * Categoria com mais produtos vinculados, ou vazio se nenhum produto está vinculado a uma
     * categoria — ver {@link EstoqueSummary#categoriaComMaisProdutos}.
     */
    Optional<CategoryProductCount> findCategoryWithMostProducts();

    /**
     * Atualiza em lote a coluna denormalizada {@code category} de todos os produtos vinculados a
     * uma categoria renomeada.
     *
     * <p>Update em massa e não carregar-e-salvar produto a produto: a operação toca só uma coluna
     * escalar, e materializar o agregado inteiro (com variações e atributos) de cada produto de
     * uma categoria grande para reescrever um texto seria caro sem nenhum ganho.</p>
     *
     * @return quantidade de produtos atualizados
     */
    int renameCategory(Long categoryId, String newName);

    /**
     * Todos os produtos de um tipo, sem paginação (Bloco 1.1) — usado para disponibilidade de
     * kits: o catálogo de kits é um subconjunto pequeno do catálogo geral, então carregar todos
     * de uma vez e paginar/filtrar em memória é seguro e evita ter que tornar
     * {@code buildableQuantity} (calculado, não uma coluna) SQL-friendly.
     */
    List<Product> findAllByType(ProductType type);

    /**
     * Contagem de produtos por categoria, para todos os ids informados, numa única consulta
     * (Bloco 2.1). Categoria sem produto vinculado não aparece no mapa (contagem zero, implícita).
     */
    Map<Long, Long> countProductsByCategoryIds(List<Long> categoryIds);

    /** Quantos produtos estão vinculados a uma categoria — usado para bloquear DELETE (Bloco 2.3). */
    long countByCategoryId(Long categoryId);

    /**
     * Atualiza em lote a coluna denormalizada {@code brand} de todos os produtos vinculados a
     * uma marca renomeada. Mesmo raciocínio de {@link #renameCategory}.
     *
     * @return quantidade de produtos atualizados
     */
    int renameBrand(Long brandId, String newName);

    /**
     * Contagem de produtos por marca, para todos os ids informados, numa única consulta. Marca
     * sem produto vinculado não aparece no mapa (contagem zero, implícita).
     */
    Map<Long, Long> countProductsByBrandIds(List<Long> brandIds);

    /** Quantos produtos estão vinculados a uma marca — usado para bloquear DELETE. */
    long countByBrandId(Long brandId);

    /**
     * Todos os produtos vinculados a uma categoria, sem paginação — usado para calcular
     * {@code averageMarginPercent} (a lista de categorias já é pequena e paginada; carregar os
     * produtos de cada uma por vez é mais simples que uma agregação SQL de margem por categoria).
     */
    List<Product> findAllByCategoryId(Long categoryId);

    /** Mesmo que {@link #findAllByCategoryId}, por marca. */
    List<Product> findAllByBrandId(Long brandId);

    /**
     * Quantidade de produtos/kits com o {@code status} informado (EST-F023) — usado para aplicar
     * o teto de 5 rascunhos no servidor antes de gravar.
     */
    long countByStatus(ProductStatus status);

    /**
     * Arquiva o produto pai (EST-F054): {@code status = ARQUIVADO} mais quando e por quem. As
     * variações seguem o pai — não têm status próprio. Não toca em {@code active} nem no saldo.
     */
    void archive(String sku, Instant archivedAt, String archivedBy);

    /** Desfaz {@link #archive}: volta para {@code ATIVO} e limpa {@code archived_at/by}. */
    void restore(String sku);

    /**
     * Indica se o SKU — do produto pai ou de uma variação — pertence a um produto arquivado
     * (EST-F054). Falso para SKU inexistente.
     */
    boolean isArchived(String sku);

    /**
     * Apaga de fato o produto e a grade dele (EST-F026). Só é chamado para rascunho sem histórico
     * de estoque — a checagem mora em {@code EstoqueService.deleteProduct}, porque é regra de
     * domínio, não do adapter.
     */
    void deleteBySku(String sku);
}
