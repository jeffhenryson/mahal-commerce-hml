package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

    Optional<ProductEntity> findBySku(String sku);

    // Cobre SKU pai e SKU de variação numa consulta só. Ambas as colunas já têm índice único
    // (uk_product_sku e uk_product_variant_sku, da V44), então o LEFT JOIN não custa varredura.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.sku = :sku OR v.sku = :sku")
    boolean existsBySkuOrVariantSku(String sku);

    // EST-F019: resolve o produto pai a partir de QUALQUER sku do catálogo — o próprio pai ou o
    // de uma variação. É o caminho de consulta de preço do PDV, que lê o código de barras da
    // variação (o sabor específico na prateleira) mas precisa do preço, que mora no pai.
    // O JOIN FETCH evita o N+1 de carregar as variações depois. EST-C026 — sem o JOIN FETCH dos
    // atributos: com variants em List, o fetch de duas coleções aninhadas repetia a variação uma vez
    // por atributo (o cigarro com Modelo + Embalagem vinha em dobro). Os atributos chegam em lote,
    // pelo @BatchSize de ProductVariantEntity.attributes.
    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v "
            + "WHERE p.sku = :sku OR p.id IN (SELECT v2.product.id FROM ProductVariantEntity v2 WHERE v2.sku = :sku)")
    Optional<ProductEntity> findByAnySku(String sku);

    /** PDV-C059 — {@link #findByAnySku} em lote: os produtos donos dos SKUs pedidos, pai ou variação. */
    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v "
            + "WHERE p.sku IN :skus OR p.id IN (SELECT v2.product.id FROM ProductVariantEntity v2 WHERE v2.sku IN :skus)")
    List<ProductEntity> findByAnySkuIn(@Param("skus") Collection<String> skus);

    // Mesmo padrão de existsBySkuOrVariantSku, mas para código de barras — cobre o EAN do próprio
    // pai e o de qualquer variação numa consulta só.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.barcode = :barcode OR v.barcode = :barcode")
    boolean existsByBarcodeOrVariantBarcode(String barcode);

    // Resolve o produto pai a partir do código de barras do próprio pai ou de qualquer variação —
    // caminho de leitura por scanner do PDV, mesmo formato de findByAnySku.
    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v "
            + "WHERE p.barcode = :barcode OR p.id IN "
            + "(SELECT v2.product.id FROM ProductVariantEntity v2 WHERE v2.barcode = :barcode)")
    Optional<ProductEntity> findByAnyBarcode(String barcode);

    // EST-F018: um SKU só conta como ativo se o produto pai estiver ativo. Para SKU de variação
    // exige-se as duas pontas — desativar o pai tira a grade inteira de circulação, sem precisar
    // percorrer variação por variação.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.active = TRUE "
            + "AND (p.sku = :sku OR (v.sku = :sku AND v.active = TRUE))")
    boolean isSkuActive(String sku);

    // EST-F054: o arquivamento mora no pai — SKU de variação herda o status dele.
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END FROM ProductEntity p "
            + "LEFT JOIN p.variants v WHERE p.status = 'ARQUIVADO' AND (p.sku = :sku OR v.sku = :sku)")
    boolean isSkuArchived(String sku);

    // EST-F054: SQL nativo porque archived_at/archived_by são somente leitura no mapeamento
    // (ver ProductEntity). Variações não têm status — seguem o pai.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE product SET status = 'ARQUIVADO', archived_at = :archivedAt, archived_by = :archivedBy "
            + "WHERE sku = :sku", nativeQuery = true)
    int archive(@Param("sku") String sku, @Param("archivedAt") java.time.Instant archivedAt,
            @Param("archivedBy") String archivedBy);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE product SET status = 'ATIVO', archived_at = NULL, archived_by = NULL "
            + "WHERE sku = :sku", nativeQuery = true)
    int restore(@Param("sku") String sku);

    /**
     * Listagem filtrada do catálogo pelo idioma {@code :param IS NULL OR ...} — uma Specification
     * daria o mesmo para quatro critérios fixos, com muito mais cerimônia. Só vale para filtros de
     * {@code String}/{@code Boolean}: para {@code Instant} este padrão tem um bug de tipagem no
     * Postgres real quando o valor é nulo (ver {@code OrderRepositoryImpl.findAll}, que por isso
     * usa Specification para o filtro de data de pedidos).
     *
     * <p>Sem {@code ORDER BY} na query, de propósito: a ordenação chega pelo {@code Pageable},
     * porque o campo é escolhido pelo cliente dentro de uma lista fechada
     * ({@code ProductSortField}). Concatenar o nome da coluna na string da query seria injeção.</p>
     *
     * <p>{@code search} chega já em minúsculas e com os {@code %} aplicados pelo adapter, e cobre
     * nome, SKU, categoria e marca — é o mesmo campo de busca único da tela do admin. Categoria e
     * marca entraram depois (EST-F029): o lojista digita "alfafa" esperando ver a categoria
     * inteira, e antes só achava o produto que por acaso tivesse a palavra no nome.</p>
     *
     * <p><b>EST-C020:</b> {@code unaccent} entra nos <b>dois</b> lados da comparação. Só na coluna
     * resolveria "carvao" achar "Carvão" e deixaria "Carvão" digitado sem achar "carvao" gravado —
     * e a base tem as duas grafias. Os filtros {@code :category} e {@code :brand} continuam igualdade
     * exata: são valor escolhido numa lista, não texto digitado — a busca parcial por eles mora
     * dentro de {@code :search}. A função é registrada por
     * {@code UnaccentFunctionContributor} e existe nos dois bancos (extensão no Postgres, alias no
     * H2 do perfil dev).</p>
     *
     * <p><b>EST-C024:</b> o {@code CAST(:search AS String)} não é decoração — sem ele o endpoint
     * inteiro caía em 500. Antes da EST-C020 o parâmetro aparecia como operando de
     * {@code LIKE :search}, e era dali que o Hibernate inferia que era texto. Envolvido em
     * {@code unaccent(...)}, que é registrada só com o tipo de retorno, ele perdeu essa âncora: a
     * outra ocorrência é {@code :search IS NULL}, igualmente sem tipo, então o bind saía como
     * objeto binário e o Postgres respondia
     * {@code ERROR: function unaccent(bytea) does not exist}. Falhava na preparação do statement,
     * portanto para qualquer valor de {@code search} — inclusive nulo, que é o caso da listagem
     * sem busca. É o mesmo bug de tipagem que o quarto parágrafo acima já registra para
     * {@code Instant}, agora alcançando {@code String}. O H2 do perfil dev não pega: o alias
     * recebe {@code Object} e aceita o bind sem reclamar.</p>
     *
     * <p>Padrão ID-first, como toda paginação do módulo: pagina só os ids e o fetch das variações
     * vem depois, por {@code findAllByIdsWithVariants} — {@code LIMIT}/{@code OFFSET} junto de
     * {@code JOIN FETCH} de coleção é o bug clássico de paginação com JPA. Substituiu o antigo
     * {@code findAllIds}, que era este mesmo com todos os filtros nulos.</p>
     *
     * <p><b>EST-F054:</b> {@code :archived} nunca é nulo (o adapter resolve nulo para falso) — com
     * {@code false} o arquivado fica fora, com {@code true} só ele volta. Primitivo de propósito:
     * pelo mesmo motivo do {@code CAST} acima, um {@code :archived IS NULL} sem tipo é bind
     * frágil.</p>
     */
    @Query("""
            SELECT p.id FROM ProductEntity p
            WHERE (:search   IS NULL
                     OR unaccent(LOWER(p.name)) LIKE unaccent(CAST(:search AS String))
                     OR unaccent(LOWER(p.sku))  LIKE unaccent(CAST(:search AS String))
                     OR unaccent(LOWER(p.category)) LIKE unaccent(CAST(:search AS String))
                     OR unaccent(LOWER(p.brand))    LIKE unaccent(CAST(:search AS String)))
              AND (:category IS NULL OR LOWER(p.category) = :category)
              AND (:brand    IS NULL OR LOWER(p.brand)    = :brand)
              AND (:active   IS NULL OR p.active = :active)
              AND (:type     IS NULL OR p.type = :type)
              AND (:kitComponentEligible IS NULL OR p.kitComponentEligible = :kitComponentEligible)
              AND (:status   IS NULL OR p.status = :status)
              AND (:sessionProduct IS NULL OR p.sessionProduct = :sessionProduct)
              AND ((:archived = TRUE AND p.status = 'ARQUIVADO')
                   OR (:archived = FALSE AND p.status <> 'ARQUIVADO'))
            """)
    Page<Long> findFilteredIds(@Param("search") String search,
            @Param("category") String category,
            @Param("brand") String brand,
            @Param("active") Boolean active,
            @Param("type") String type,
            @Param("kitComponentEligible") Boolean kitComponentEligible,
            @Param("status") String status,
            @Param("sessionProduct") Boolean sessionProduct,
            @Param("archived") boolean archived,
            Pageable pageable);

    /** EST-F023 — conta rascunhos para aplicar o teto de 5 no servidor. */
    long countByStatus(String status);

    /** Bloco 1.1 — todos os kits do catálogo, sem paginação (subconjunto pequeno do catálogo geral). */
    List<ProductEntity> findByType(String type);

    /**
     * Contagem de produtos por categoria, numa única query agregada (Bloco 2.1) — evita N+1 ao
     * montar {@code productCount} por página de {@code GET /estoque/categories}.
     */
    @Query("SELECT p.categoryId, COUNT(p) FROM ProductEntity p WHERE p.categoryId IN :categoryIds GROUP BY p.categoryId")
    List<Object[]> countProductsByCategoryIdsRaw(@Param("categoryIds") List<Long> categoryIds);

    /** Bloco 2.3 — usado para bloquear DELETE de categoria com produto vinculado. */
    long countByCategoryId(Long categoryId);

    /** Usado para calcular {@code averageMarginPercent} de uma categoria (sem paginação). */
    List<ProductEntity> findByCategoryId(Long categoryId);

    /** Mesmo que {@link #findByCategoryId}, por marca. */
    List<ProductEntity> findByBrandId(Long brandId);

    /**
     * Contagem de produtos por marca, para todos os ids informados, numa única consulta — mesmo
     * padrão de {@link #countProductsByCategoryIdsRaw}.
     */
    @Query("SELECT p.brandId, COUNT(p) FROM ProductEntity p WHERE p.brandId IN :brandIds GROUP BY p.brandId")
    List<Object[]> countProductsByBrandIdsRaw(@Param("brandIds") List<Long> brandIds);

    /** Usado para bloquear DELETE de marca com produto vinculado. */
    long countByBrandId(Long brandId);

    /** Propaga o rename da marca para a coluna denormalizada — mesmo padrão de {@link #renameCategory}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.brand = :newName WHERE p.brandId = :brandId")
    int renameBrand(@Param("brandId") Long brandId, @Param("newName") String newName);

    // ECM-F002: mesma condição de Pricing#isPriced() traduzida para SQL — salePrice preenchido,
    // ou custo+markup preenchidos o bastante para sugerir um preço. Kit nunca tem costPrice
    // próprio (KitCostNotEditableException), então só o ramo do salePrice se aplica a ele.
    // onSale segue o mesmo idioma de filtro opcional de findFilteredIds acima: nulo não filtra.
    /**
     * Catálogo público, ordenado pela vitrine: categoria em destaque primeiro, depois a ordem de
     * exibição da categoria, e só então o id — que segue como desempate final, porque destaque e
     * ordem não são únicos e sem ele a paginação seria instável (EST-C012).
     *
     * <p>{@code LEFT JOIN} e não {@code JOIN}: produto sem categoria vinculada continua no
     * catálogo. Os {@code COALESCE} colocam esse produto no grupo dos não-destacados e no fim da
     * ordem, em vez de deixá-lo à mercê do tratamento de NULL do banco, que difere entre
     * PostgreSQL e H2.</p>
     *
     * <p>{@code search} segue a mesma regra de {@link #findFilteredIds} (EST-F029): minúsculas,
     * {@code %} aplicados pelo adapter, sem acento nos dois lados, e o {@code CAST} do EST-C024.</p>
     */
    @Query("SELECT p.id FROM ProductEntity p "
            + "LEFT JOIN ProductCategoryEntity c ON c.id = p.categoryId "
            + "WHERE p.active = TRUE AND p.status = 'ATIVO' "
            + "AND (p.salePrice IS NOT NULL OR (p.costPrice IS NOT NULL AND p.markupPercent IS NOT NULL)) "
            + "AND (:onSale IS NULL OR p.onSale = :onSale) "
            + "AND (:visible IS NULL OR p.visibleInMarketplace = :visible) "
            + "AND (:categoryId IS NULL OR p.categoryId = :categoryId) "
            + "AND (:search IS NULL "
            + "     OR unaccent(LOWER(p.name))     LIKE unaccent(CAST(:search AS String)) "
            + "     OR unaccent(LOWER(p.sku))      LIKE unaccent(CAST(:search AS String)) "
            + "     OR unaccent(LOWER(p.category)) LIKE unaccent(CAST(:search AS String)) "
            + "     OR unaccent(LOWER(p.brand))    LIKE unaccent(CAST(:search AS String))) "
            + "ORDER BY COALESCE(c.featured, FALSE) DESC, COALESCE(c.displayOrder, 2147483647) ASC, p.id ASC")
    Page<Long> findActivePricedIds(Pageable pageable, @Param("onSale") Boolean onSale,
            @Param("categoryId") Long categoryId, @Param("search") String search,
            @Param("visible") Boolean visibleInMarketplace);

    /** ECM-F013 — quais destes SKUs existem, para recusar o lote inteiro se faltar algum. */
    @Query("SELECT p.sku FROM ProductEntity p WHERE p.sku IN :skus")
    List<String> findExistingSkus(@Param("skus") java.util.Collection<String> skus);

    /** ECM-F013 — liga ou desliga o produto no app em lote (Central de Pedidos). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.visibleInMarketplace = :visible WHERE p.sku IN :skus")
    int updateVisibleInMarketplace(@Param("skus") java.util.Collection<String> skus,
            @Param("visible") boolean visible);

    @Query("SELECT DISTINCT p FROM ProductEntity p LEFT JOIN FETCH p.variants v "
            + "WHERE p.id IN :ids ORDER BY p.id")
    List<ProductEntity> findAllByIdsWithVariants(List<Long> ids);

    // EST-F022 — GET /estoque/summary. COUNT(v) e não v.id: variantes sem atributos ainda contam.
    // EST-F054: só produto publicado conta — rascunho e arquivado não são mercadoria da loja.
    @Query("SELECT COUNT(v) FROM ProductVariantEntity v WHERE v.product.status = 'ATIVO'")
    long countVariants();

    @Query("SELECT COUNT(p) FROM ProductEntity p WHERE p.status = 'ATIVO'")
    long countPublished();

    // Categoria com mais produtos vinculados, para o KPI de resumo. Só considera categoryId
    // preenchido — produto sem vínculo não participa da contagem por categoria nenhuma.
    // Pageable.ofSize(1) traz só a linha vencedora, sem precisar de um LIMIT nativo.
    @Query("SELECT c.name, COUNT(p) FROM ProductEntity p JOIN ProductCategoryEntity c ON c.id = p.categoryId "
            + "WHERE p.categoryId IS NOT NULL AND p.status = 'ATIVO' GROUP BY c.name ORDER BY COUNT(p) DESC, c.name ASC")
    List<Object[]> findCategoryWithMostProductsRaw(Pageable pageable);

    // Propaga o rename da categoria para a coluna denormalizada. @Modifying com
    // clearAutomatically: o update ignora o contexto de persistência, então instâncias já
    // carregadas ficariam com o nome antigo até o fim da transação.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.category = :newName WHERE p.categoryId = :categoryId")
    int renameCategory(@Param("categoryId") Long categoryId, @Param("newName") String newName);
}
