package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.AttributeType;
import com.cernecommerce.core.domain.model.estoque.Brand;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.domain.model.estoque.CategoryProductCount;
import com.cernecommerce.core.domain.model.estoque.ReplenishmentListItem;
import com.cernecommerce.core.domain.model.estoque.KitComponent;
import com.cernecommerce.core.domain.model.estoque.LotIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.ProductAttribute;
import com.cernecommerce.core.domain.model.estoque.ProductFilter;
import com.cernecommerce.core.domain.model.estoque.ProductSortField;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;
import com.cernecommerce.core.domain.model.estoque.ProductVariant;
import com.cernecommerce.core.domain.model.estoque.ReorderAlertCounts;
import com.cernecommerce.core.domain.model.estoque.ReorderPoint;
import com.cernecommerce.core.domain.model.estoque.ReservationIntegrityMismatch;
import com.cernecommerce.core.domain.model.estoque.StockBalance;
import com.cernecommerce.core.domain.model.estoque.StockCount;
import com.cernecommerce.core.domain.model.estoque.StockCountItem;
import com.cernecommerce.core.domain.model.estoque.StockCountStatus;
import com.cernecommerce.core.domain.model.estoque.StockLot;
import com.cernecommerce.core.domain.model.estoque.StockMovement;
import com.cernecommerce.core.domain.model.estoque.StockReservation;
import com.cernecommerce.core.domain.model.estoque.Warehouse;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Testa os adapters de persistência de estoque contra banco real (EST-C007).
 *
 * <p>Nenhum {@code *RepositoryImpl} de estoque era exercitado diretamente: o mapeamento
 * domínio↔entidade, o padrão ID-first da listagem de produtos e a propagação do {@code version}
 * só apareciam de lado, por ITs de controller que passariam mesmo com o mapeamento errado em
 * detalhes.</p>
 *
 * <p><b>Por que não é um {@code @DataJpaTest}:</b> no Spring Boot 4 as slices de teste saíram do
 * {@code spring-boot-test-autoconfigure} (que ficou com 22 classes) para módulos por tecnologia,
 * e o artefato que traz {@code @DataJpaTest}/{@code @AutoConfigureTestDatabase} não está no
 * classpath deste projeto. Daí o {@code @SpringBootTest} + {@code @Transactional}, que é o padrão
 * já usado pelos demais ITs.</p>
 *
 * <p>Cada teste roda numa transação que sofre rollback ao final. Onde a asserção depende do que
 * foi realmente gravado, há {@code flush()} + {@code clear()} explícitos — sem isso a leitura
 * voltaria do cache de primeiro nível e não provaria nada sobre o mapeamento.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class EstoqueRepositoryIT {

    @Autowired ProductRepositoryImpl productRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired WarehouseRepositoryImpl warehouseRepository;
    @Autowired StockBalanceRepositoryImpl stockBalanceRepository;
    @Autowired StockMovementRepositoryImpl stockMovementRepository;
    @Autowired ReorderPointRepositoryImpl reorderPointRepository;
    @Autowired StockIntegrityRepositoryImpl stockIntegrityRepository;
    @Autowired StockCountRepositoryImpl stockCountRepository;
    @Autowired StockReservationRepositoryImpl stockReservationRepository;
    @Autowired StockLotRepositoryImpl stockLotRepository;
    @Autowired KitComponentRepositoryImpl kitComponentRepository;

    @PersistenceContext EntityManager em;

    /** Força a ida ao banco: sem isso a releitura viria do cache de primeiro nível. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Warehouse givenWarehouse(String code) {
        return warehouseRepository.save(Warehouse.create(code, "Depósito " + code, WarehouseType.LOJA_FISICA));
    }

    @Test
    void product_roundTrip_preservaVariacoesEAtributos() {
        Product saved = productRepository.save(Product.create("RT-001", "Narguilé", "narguile",
                List.of(ProductVariant.create("RT-001-M", List.of(new ProductAttribute("sabor", "menta"))))));
        flushAndClear();

        Optional<Product> found = productRepository.findBySku("RT-001");

        assertThat(found).isPresent();
        assertThat(found.get().id()).isNotNull().isEqualTo(saved.id());
        assertThat(found.get().name()).isEqualTo("Narguilé");
        assertThat(found.get().active()).isTrue();
        assertThat(found.get().variants()).singleElement()
                .satisfies(v -> {
                    assertThat(v.sku()).isEqualTo("RT-001-M");
                    assertThat(v.attributes()).containsExactly(new ProductAttribute("sabor", "menta"));
                });
    }

    /**
     * EST-C026 — variação com dois atributos (Modelo + Embalagem, o cigarro) vinha em dobro nas três
     * consultas com JOIN FETCH: a lista de variações recebia uma linha por atributo.
     */
    @Test
    void product_variacoesComDoisAtributos_naoVemRepetidasNasConsultasComFetch() {
        Product saved = productRepository.save(Product.create("RT-CIG-001", "Marlboro", "cigarros",
                List.of(ProductVariant.create("RT-CIG-001-GOLD-MACO", List.of(new ProductAttribute("Modelo", "Gold"),
                                new ProductAttribute("Embalagem", "Maço")), null, "7892222222226"),
                        ProductVariant.create("RT-CIG-001-GOLD-CART", List.of(new ProductAttribute("Modelo", "Gold"),
                                new ProductAttribute("Embalagem", "Carteira")), null, null))));
        flushAndClear();

        assertThat(productRepository.findByAnySku("RT-CIG-001-GOLD-MACO")).isPresent().get()
                .satisfies(p -> {
                    assertThat(p.variants()).extracting(ProductVariant::sku)
                            .containsExactlyInAnyOrder("RT-CIG-001-GOLD-MACO", "RT-CIG-001-GOLD-CART");
                    assertThat(p.variants()).allSatisfy(v -> assertThat(v.attributes()).hasSize(2));
                });
        flushAndClear();
        assertThat(productRepository.findByBarcode("7892222222226")).isPresent().get()
                .satisfies(p -> assertThat(p.variants()).hasSize(2));
        flushAndClear();
        assertThat(productJpaRepository.findAllByIdsWithVariants(List.of(saved.id()))).singleElement()
                .satisfies(e -> assertThat(e.getVariants()).hasSize(2));
    }

    @Test
    void product_roundTrip_preservaLotTracked() {
        productRepository.save(Product.create("RT-LOT-001", "Essência", "essencia", List.of())
                .withLotTracked(true));
        flushAndClear();

        assertThat(productRepository.findBySku("RT-LOT-001")).isPresent()
                .get().satisfies(p -> assertThat(p.lotTracked()).isTrue());
    }

    // ---------- Campos aditivos de catálogo (barcode, unit, sampleProduct, kitComponentEligible,
    // causeAmount, visibilidade por canal) ----------

    @Test
    void product_roundTrip_preservaCamposAditivosDeCatalogo() {
        productRepository.save(Product.create("RT-ADD-001", "Narguilé", "narguile", List.of())
                .withBarcode("7891234567895")
                .withUnit(MeasurementUnit.CX)
                .withSampleProduct(true)
                .withKitComponentEligible(true)
                .withVisibleInPos(false)
                .withVisibleInMarketplace(false));
        flushAndClear();

        Product found = productRepository.findBySku("RT-ADD-001").orElseThrow();

        assertThat(found.barcode()).isEqualTo("7891234567895");
        assertThat(found.unit()).isEqualTo(MeasurementUnit.CX);
        assertThat(found.sampleProduct()).isTrue();
        assertThat(found.kitComponentEligible()).isTrue();
        assertThat(found.visibleInPos()).isFalse();
        assertThat(found.visibleInMarketplace()).isFalse();
    }

    /** Nenhum campo aditivo informado: unidade default UN, visibilidade default true nos dois canais. */
    @Test
    void product_semCamposAditivos_voltaComDefaultsDeCompatibilidade() {
        productRepository.save(Product.create("RT-ADD-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        Product found = productRepository.findBySku("RT-ADD-002").orElseThrow();

        assertThat(found.barcode()).isNull();
        assertThat(found.unit()).isEqualTo(MeasurementUnit.UN);
        assertThat(found.sampleProduct()).isFalse();
        assertThat(found.kitComponentEligible()).isFalse();
        assertThat(found.visibleInPos()).isTrue();
        assertThat(found.visibleInMarketplace()).isTrue();
    }

    @Test
    void product_barcode_existsEFindByBarcodeResolvemPeloPaiOuPelaVariacao() {
        productRepository.save(Product.create("RT-BAR-001", "Essência", "essencia",
                        List.of(ProductVariant.create("RT-BAR-001-M", List.of(), null, "7891111111116")))
                .withBarcode("7890000000006"));
        flushAndClear();

        assertThat(productRepository.existsByBarcode("7890000000006")).isTrue();
        assertThat(productRepository.existsByBarcode("7891111111116")).isTrue();
        assertThat(productRepository.existsByBarcode("0000000000000")).isFalse();

        assertThat(productRepository.findByBarcode("7891111111116"))
                .isPresent().get().satisfies(p -> assertThat(p.sku()).isEqualTo("RT-BAR-001"));
    }

    @Test
    void pricing_roundTrip_preservaCauseAmountNoProdutoENaVariacao() {
        productRepository.save(Product.create("RT-CAUSE-001", "Essência", "essencia",
                List.of(ProductVariant.create("RT-CAUSE-001-M", List.of(),
                        Pricing.of(null, null, null, null, new BigDecimal("1.00")))),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("30.00"), null, new BigDecimal("2.00"))));
        flushAndClear();

        Product found = productRepository.findBySku("RT-CAUSE-001").orElseThrow();

        assertThat(found.pricing().causeAmount()).isEqualByComparingTo("2.00");
        ProductVariant variant = found.variants().get(0);
        assertThat(variant.hasOwnPricing()).isTrue();
        assertThat(variant.pricing().causeAmount()).isEqualByComparingTo("1.00");
        // Herança por campo: a variação só declarou causeAmount próprio, o resto vem do pai.
        assertThat(found.effectivePricingFor("RT-CAUSE-001-M").salePrice()).isEqualByComparingTo("30.00");
    }

    /**
     * Único teste que de fato exercita o mapeamento {@code @ElementCollection}+
     * {@code @OrderColumn} da galeria de imagens contra banco real — mocks não capturam erro
     * de mapeamento JPA.
     */
    @Test
    void product_roundTrip_preservaCamposDeMarketingEOrdemDaGaleria() {
        productRepository.save(Product.create("RT-MKT-001", "Narguilé", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), null, new BigDecimal("79.90"), new BigDecimal("99.90")),
                ProductType.SIMPLES, false, "Aladin", "http://img.png", true, true, "Descrição longa",
                "http://video.mp4", List.of("http://img1.png", "http://img2.png", "http://img3.png")));
        flushAndClear();

        Product found = productRepository.findBySku("RT-MKT-001").orElseThrow();

        assertThat(found.pricing().originalPrice()).isEqualByComparingTo("99.90");
        assertThat(found.superPromo()).isTrue();
        assertThat(found.description()).isEqualTo("Descrição longa");
        assertThat(found.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(found.images()).as("ordem da galeria preservada")
                .containsExactly("http://img1.png", "http://img2.png", "http://img3.png");
    }

    @Test
    void product_semCamposDeMarketing_voltaComGaleriaVaziaECamposNulos() {
        productRepository.save(Product.create("RT-MKT-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        Product found = productRepository.findBySku("RT-MKT-002").orElseThrow();

        assertThat(found.pricing().originalPrice()).isNull();
        assertThat(found.superPromo()).isFalse();
        assertThat(found.description()).isNull();
        assertThat(found.videoUrl()).isNull();
        assertThat(found.images()).isEmpty();
    }

    @Test
    void product_atualizarGaleria_substituiAListaInteiraSemDuplicar() {
        Product saved = productRepository.save(Product.create("RT-MKT-003", "Narguilé", "narguile", List.of())
                .withImages(List.of("http://old1.png", "http://old2.png")));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-MKT-003").orElseThrow();
        productRepository.save(reloaded.withImages(List.of("http://new1.png")));
        flushAndClear();

        Product found = productRepository.findBySku("RT-MKT-003").orElseThrow();
        assertThat(found.images()).containsExactly("http://new1.png");
    }

    // ---------- EST-F019 — precificação ----------

    @Test
    void product_roundTrip_preservaAPrecificacao() {
        productRepository.save(Product.create("PR-001", "Narguilé", "narguile", List.of(),
                Pricing.of(new BigDecimal("45.00"), new BigDecimal("80.5000"), new BigDecimal("79.90"))));
        flushAndClear();

        Pricing pricing = productRepository.findBySku("PR-001").orElseThrow().pricing();

        assertThat(pricing.costPrice()).isEqualByComparingTo("45.00");
        assertThat(pricing.markupPercent()).isEqualByComparingTo("80.5000");
        assertThat(pricing.salePrice()).isEqualByComparingTo("79.90");
    }

    /**
     * Produto sem preço tem que voltar como {@link Pricing#empty()}, e não com zeros: preço
     * desconhecido não é preço zero, e a diferença decide se o PDV recusa a venda ou dá o item
     * de graça.
     */
    @Test
    void product_semPreco_voltaComoPricingVazio() {
        productRepository.save(Product.create("PR-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        assertThat(productRepository.findBySku("PR-002").orElseThrow().pricing().isEmpty()).isTrue();
    }

    /** A escala 4 de markup_percent existe para sobreviver ao round-trip sem truncar. */
    @Test
    void markupPercent_preservaAsQuatroCasasDecimais() {
        productRepository.save(Product.create("PR-003", "Essência", "essencia", List.of(),
                Pricing.byMarkup(new BigDecimal("45.00"), new BigDecimal("33.3333"))));
        flushAndClear();

        Pricing pricing = productRepository.findBySku("PR-003").orElseThrow().pricing();

        assertThat(pricing.markupPercent()).isEqualByComparingTo("33.3333");
        assertThat(pricing.suggestedPrice()).isEqualByComparingTo("60.00");
    }

    @Test
    void findByAnySku_resolveOPaiTantoPeloSkuPaiQuantoPeloDeVariacao() {
        productRepository.save(Product.create("PR-004", "Essência", "essencia",
                List.of(ProductVariant.create("PR-004-UVA", List.of(new ProductAttribute("sabor", "uva")))),
                Pricing.of(null, null, new BigDecimal("29.90"))));
        flushAndClear();

        Product pelosPai = productRepository.findByAnySku("PR-004").orElseThrow();
        Product pelaVariacao = productRepository.findByAnySku("PR-004-UVA").orElseThrow();

        assertThat(pelosPai.sku()).isEqualTo("PR-004");
        assertThat(pelaVariacao.sku()).as("variação resolve para o produto pai").isEqualTo("PR-004");
        assertThat(pelaVariacao.pricing().effectivePrice()).as("e herda o preço dele")
                .isEqualByComparingTo("29.90");
        assertThat(productRepository.findByAnySku("PR-999")).as("SKU inexistente").isEmpty();
    }

    @Test
    void existsBySku_encontraTantoSkuPaiQuantoSkuDeVariacao() {
        productRepository.save(Product.create("EX-001", "Essência", "essencia",
                List.of(ProductVariant.create("EX-001-UVA", List.of(new ProductAttribute("sabor", "uva"))))));
        flushAndClear();

        // É a checagem que sustenta o EST-C002: se ela não enxergar o SKU de variação, toda venda
        // de variação passa a ser recusada com 404.
        assertThat(productRepository.existsBySku("EX-001")).as("SKU pai").isTrue();
        assertThat(productRepository.existsBySku("EX-001-UVA")).as("SKU de variação").isTrue();
        assertThat(productRepository.existsBySku("EX-999")).as("SKU inexistente").isFalse();
    }

    /**
     * EST-F018: {@code isSkuActive} é distinto de {@code existsBySku} — o SKU continua existindo
     * depois de desativado, e a diferença entre os dois é o que separa 404 de 409.
     */
    @Test
    void isSkuActive_exigeProdutoPaiAtivo_inclusiveParaSkuDeVariacao() {
        Product saved = productRepository.save(Product.create("AT-001", "Essência", "essencia",
                List.of(ProductVariant.create("AT-001-UVA", List.of(new ProductAttribute("sabor", "uva"))))));
        flushAndClear();

        assertThat(productRepository.isSkuActive("AT-001")).as("pai ativo").isTrue();
        assertThat(productRepository.isSkuActive("AT-001-UVA")).as("variação de pai ativo").isTrue();

        productRepository.save(saved.withActive(false));
        flushAndClear();

        assertThat(productRepository.existsBySku("AT-001"))
                .as("desativar não apaga: o SKU continua existindo").isTrue();
        assertThat(productRepository.isSkuActive("AT-001")).as("pai desativado").isFalse();
        assertThat(productRepository.isSkuActive("AT-001-UVA"))
                .as("desativar o pai tira a grade inteira de circulação").isFalse();
    }

    @Test
    void isSkuActive_falseParaSkuInexistente() {
        assertThat(productRepository.isSkuActive("NAO-EXISTE-999")).isFalse();
    }

    /** O PATCH carrega o produto inteiro e regrava: as variações não podem sumir no caminho. */
    @Test
    void save_aposWithDetails_preservaVariacoesEAtributos() {
        Product saved = productRepository.save(Product.create("UP-001", "Nome Antigo", "cat-antiga",
                List.of(ProductVariant.create("UP-001-M", List.of(new ProductAttribute("sabor", "menta"))))));
        flushAndClear();

        Product carregado = productRepository.findBySku("UP-001").orElseThrow();
        productRepository.save(carregado.withDetails("Nome Novo", null));
        flushAndClear();

        assertThat(productRepository.findBySku("UP-001")).get().satisfies(p -> {
            assertThat(p.id()).isEqualTo(saved.id());
            assertThat(p.name()).isEqualTo("Nome Novo");
            assertThat(p.category()).as("null mantém").isEqualTo("cat-antiga");
            assertThat(p.variants()).singleElement().satisfies(v -> {
                assertThat(v.sku()).isEqualTo("UP-001-M");
                assertThat(v.attributes()).containsExactly(new ProductAttribute("sabor", "menta"));
            });
        });
    }

    @Test
    void warehouse_saveAposWithActive_persisteADesativacao() {
        Warehouse warehouse = givenWarehouse("WH-OFF");
        flushAndClear();

        warehouseRepository.save(warehouse.withActive(false));
        flushAndClear();

        assertThat(warehouseRepository.findByCode("WH-OFF")).get()
                .satisfies(w -> assertThat(w.active()).isFalse());
    }

    @Test
    void existsBySku_produtoSemVariacao_soCasaComOProprioSku() {
        productRepository.save(Product.create("EX-002", "Carvão", "carvao", List.of()));
        flushAndClear();

        assertThat(productRepository.existsBySku("EX-002")).isTrue();
        assertThat(productRepository.existsBySku("EX-002-X")).isFalse();
    }

    @Test
    void findAll_paginaComIdFirst_semPerderVariacoes() {
        productRepository.save(Product.create("PG-001", "Produto 1", "cat",
                List.of(ProductVariant.create("PG-001-A", List.of(new ProductAttribute("cor", "azul"))))));
        productRepository.save(Product.create("PG-002", "Produto 2", "cat", List.of()));
        flushAndClear();

        PageResult<Product> page = productRepository.findAll(0, 1);

        assertThat(page.content()).hasSize(1);
        assertThat(page.size()).isEqualTo(1);
        assertThat(page.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(page.totalPages()).isGreaterThanOrEqualTo(2);
    }

    // ── Categorias do catálogo ───────────────────────────────────────────────

    @Autowired CategoryRepositoryImpl categoryRepository;

    @Test
    void category_roundTrip() {
        Category saved = categoryRepository.save(Category.create("CatRT Narguilé", true, 3));
        flushAndClear();

        Category reloaded = categoryRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("CatRT Narguilé");
        assertThat(reloaded.featured()).isTrue();
        assertThat(reloaded.displayOrder()).isEqualTo(3);
        assertThat(reloaded.active()).isTrue();
    }

    /** PDV-F045 — a marcação de cigarros persiste, e findCigarette traz as marcadas, ativas ou não. */
    @Test
    void category_cigarette_roundTripEFindCigarette() {
        Category cig = categoryRepository.save(Category.create("CatCig Cigarros", false, 0, true));
        Category inativa = categoryRepository.save(Category.create("CatCig Palheiros", false, 0, true)
                .withActive(false));
        Category comum = categoryRepository.save(Category.create("CatCig Isqueiros"));
        flushAndClear();

        assertThat(categoryRepository.findById(cig.id()).orElseThrow().cigarette()).isTrue();
        assertThat(categoryRepository.findById(comum.id()).orElseThrow().cigarette()).isFalse();
        assertThat(categoryRepository.findCigarette()).extracting(Category::id)
                .contains(cig.id(), inativa.id())
                .doesNotContain(comum.id());
    }

    @Test
    void category_findByName_ignoraCaixa() {
        // É o caminho de compatibilidade: o admin ainda manda texto livre, e "Narguilé" e
        // "narguilé" não podem virar duas categorias.
        categoryRepository.save(Category.create("CatCase Essência"));
        flushAndClear();

        assertThat(categoryRepository.findByName("catcase essência")).isPresent();
        assertThat(categoryRepository.findByName("CATCASE ESSÊNCIA")).isPresent();
    }

    @Test
    void category_findActiveOrdered_destacadasPrimeiroDepoisOrdemDepoisNome() {
        categoryRepository.save(Category.create("ZZ Ord Comum B", false, 1));
        categoryRepository.save(Category.create("ZZ Ord Comum A", false, 0));
        categoryRepository.save(Category.create("ZZ Ord Destaque B", true, 1));
        categoryRepository.save(Category.create("ZZ Ord Destaque A", true, 0));
        Category inativa = categoryRepository.save(Category.create("ZZ Ord Inativa", true, 0));
        categoryRepository.save(inativa.withActive(false));
        flushAndClear();

        List<String> nomes = categoryRepository.findActiveOrdered().stream()
                .map(Category::name)
                .filter(n -> n.startsWith("ZZ Ord"))
                .toList();

        assertThat(nomes).containsExactly(
                "ZZ Ord Destaque A", "ZZ Ord Destaque B", "ZZ Ord Comum A", "ZZ Ord Comum B");
        assertThat(nomes).doesNotContain("ZZ Ord Inativa");
    }

    @Test
    void renameCategory_atualizaAColunaDenormalizadaDosProdutosVinculados() {
        Category categoria = categoryRepository.save(Category.create("Ren Antiga"));
        productRepository.save(Product.create("REN-001", "Produto 1", "Ren Antiga", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(),
                categoria.id(), null));
        productRepository.save(Product.create("REN-002", "Produto 2", "Ren Antiga", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(),
                categoria.id(), null));
        // Produto de outra categoria não pode ser arrastado pelo rename.
        productRepository.save(Product.create("REN-003", "Produto 3", "Outra", List.of()));
        flushAndClear();

        int atualizados = productRepository.renameCategory(categoria.id(), "Ren Nova");
        flushAndClear();

        assertThat(atualizados).isEqualTo(2);
        assertThat(productRepository.findBySku("REN-001").orElseThrow().category()).isEqualTo("Ren Nova");
        assertThat(productRepository.findBySku("REN-002").orElseThrow().category()).isEqualTo("Ren Nova");
        assertThat(productRepository.findBySku("REN-003").orElseThrow().category()).isEqualTo("Outra");
    }

    @Test
    void product_roundTripDoVinculoDeCategoria() {
        Category categoria = categoryRepository.save(Category.create("Vinc Narguilé"));
        productRepository.save(Product.create("VINC-001", "Produto", "Vinc Narguilé", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(),
                categoria.id(), null));
        flushAndClear();

        Product reloaded = productRepository.findBySku("VINC-001").orElseThrow();

        assertThat(reloaded.categoryId()).isEqualTo(categoria.id());
        assertThat(reloaded.category()).isEqualTo("Vinc Narguilé");
    }

    @Test
    void product_semVinculoDeCategoria_segueValido() {
        productRepository.save(Product.create("VINC-002", "Produto", "texto solto", List.of()));
        flushAndClear();

        Product reloaded = productRepository.findBySku("VINC-002").orElseThrow();

        assertThat(reloaded.categoryId()).isNull();
        assertThat(reloaded.category()).isEqualTo("texto solto");
    }

    @Test
    void catalogoPublico_ordenaPorDestaqueEOrdemDaCategoria() {
        Category destaque = categoryRepository.save(Category.create("Cat Vitrine Destaque", true, 0));
        Category comum = categoryRepository.save(Category.create("Cat Vitrine Comum", false, 5));
        Pricing precificado = new Pricing(null, null, new BigDecimal("10.00"), null, null);
        // Gravado na ordem inversa da esperada, para a ordenação ter o que provar.
        productRepository.save(Product.create("VIT-001", "Comum", "Cat Vitrine Comum", List.of(), precificado,
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(), comum.id(),
                null));
        productRepository.save(Product.create("VIT-002", "Destacado", "Cat Vitrine Destaque", List.of(), precificado,
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(), destaque.id(),
                null));
        flushAndClear();

        List<String> skus = productRepository.findAllActiveAndPriced(0, 100, null, null).content().stream()
                .map(Product::sku)
                .filter(sku -> sku.startsWith("VIT-"))
                .toList();

        assertThat(skus).containsExactly("VIT-002", "VIT-001");
    }

    @Test
    void catalogoPublico_produtoSemCategoriaNaoSomeEVaiParaOFim() {
        // LEFT JOIN + COALESCE: sem eles o produto sem categoria sumiria (JOIN) ou cairia numa
        // posição dependente de como o banco trata NULL na ordenação.
        Category destaque = categoryRepository.save(Category.create("Cat Orfa Destaque", true, 0));
        Pricing precificado = new Pricing(null, null, new BigDecimal("10.00"), null, null);
        productRepository.save(Product.create("ORF-001", "Sem categoria", null, List.of(), precificado));
        productRepository.save(Product.create("ORF-002", "Destacado", "Cat Orfa Destaque", List.of(), precificado,
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(),
                destaque.id(), null));
        flushAndClear();

        List<String> skus = productRepository.findAllActiveAndPriced(0, 100, null, null).content().stream()
                .map(Product::sku)
                .filter(sku -> sku.startsWith("ORF-"))
                .toList();

        assertThat(skus).containsExactly("ORF-002", "ORF-001");
    }

    @Test
    void catalogoPublico_filtraPorCategoria() {
        Category alvo = categoryRepository.save(Category.create("Cat Filtro Alvo"));
        Category outra = categoryRepository.save(Category.create("Cat Filtro Outra"));
        Pricing precificado = new Pricing(null, null, new BigDecimal("10.00"), null, null);
        productRepository.save(Product.create("FIL-001", "Alvo", "Cat Filtro Alvo", List.of(), precificado,
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(), alvo.id(),
                null));
        productRepository.save(Product.create("FIL-002", "Outra", "Cat Filtro Outra", List.of(), precificado,
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(), outra.id(),
                null));
        flushAndClear();

        PageResult<Product> page = productRepository.findAllActiveAndPriced(0, 100, null, alvo.id());

        assertThat(page.content()).extracting(Product::sku).containsExactly("FIL-001");
        assertThat(page.totalElements()).isEqualTo(1L);
    }

    // ── Preço próprio da variação (EST-F020) ─────────────────────────────────

    @Test
    void variant_roundTripDoPrecoProprio() {
        productRepository.save(Product.create("RT-VP-001", "Essência", "essencia",
                List.of(ProductVariant.create("RT-VP-001-100G", List.of(),
                        new Pricing(new BigDecimal("40.00"), new BigDecimal("50.0000"),
                                new BigDecimal("99.90"), new BigDecimal("120.00"), null))),
                new Pricing(new BigDecimal("10.00"), null, new BigDecimal("30.00"), null, null)));
        flushAndClear();

        ProductVariant reloaded = productRepository.findBySku("RT-VP-001").orElseThrow()
                .variants().get(0);

        assertThat(reloaded.hasOwnPricing()).isTrue();
        assertThat(reloaded.pricing().costPrice()).isEqualByComparingTo("40.00");
        assertThat(reloaded.pricing().markupPercent()).isEqualByComparingTo("50.0000");
        assertThat(reloaded.pricing().salePrice()).isEqualByComparingTo("99.90");
        assertThat(reloaded.pricing().originalPrice()).isEqualByComparingTo("120.00");
    }

    @Test
    void variant_semPrecoProprioVoltaComPricingNuloENaoVazio() {
        // O null é o que representa "herda do pai"; um Pricing vazio significaria outra coisa.
        productRepository.save(Product.create("RT-VP-002", "Essência", "essencia",
                List.of(ProductVariant.create("RT-VP-002-MENTA", List.of())),
                new Pricing(null, null, new BigDecimal("30.00"), null, null)));
        flushAndClear();

        ProductVariant reloaded = productRepository.findBySku("RT-VP-002").orElseThrow()
                .variants().get(0);

        assertThat(reloaded.pricing()).isNull();
        assertThat(reloaded.hasOwnPricing()).isFalse();
    }

    @Test
    void variant_precoParcialSobreviveAoRoundTripEHerdaOResto() {
        productRepository.save(Product.create("RT-VP-003", "Essência", "essencia",
                List.of(ProductVariant.create("RT-VP-003-A", List.of(),
                        Pricing.of(new BigDecimal("40.00"), null, null))),
                new Pricing(new BigDecimal("10.00"), null, new BigDecimal("30.00"), null, null)));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-VP-003").orElseThrow();
        Pricing efetivo = reloaded.effectivePricingFor("RT-VP-003-A");

        assertThat(efetivo.costPrice()).as("próprio").isEqualByComparingTo("40.00");
        assertThat(efetivo.salePrice()).as("herdado").isEqualByComparingTo("30.00");
    }

    @Test
    void variant_precoProprioPodeSerRemovidoVoltandoAHerdar() {
        productRepository.save(Product.create("RT-VP-004", "Essência", "essencia",
                List.of(ProductVariant.create("RT-VP-004-A", List.of(),
                        Pricing.of(null, null, new BigDecimal("99.90")))),
                new Pricing(null, null, new BigDecimal("30.00"), null, null)));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-VP-004").orElseThrow();
        // Não há atalho no agregado para zerar o preço da grade inteira — reconstrói-se o produto
        // com cada variação voltando a herdar, que é o caminho que o service percorreria.
        Product semPrecoNaVariacao = Product.of(reloaded.id(), reloaded.sku(), reloaded.name(),
                reloaded.category(), reloaded.active(),
                reloaded.variants().stream().map(v -> v.withPricing(null)).toList(),
                reloaded.pricing());
        productRepository.save(semPrecoNaVariacao);
        flushAndClear();

        assertThat(productRepository.findBySku("RT-VP-004").orElseThrow()
                .variants().get(0).pricing()).isNull();
    }

    // ── Atributos do próprio SKU pai ─────────────────────────────────────────

    @Test
    void product_roundTripDosAtributosDeRaiz() {
        productRepository.save(Product.create("RT-ATR-001", "Essência", "essencia", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil"), new ProductAttribute("Peso", "50g"))));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-ATR-001").orElseThrow();

        assertThat(reloaded.attributes())
                .extracting(ProductAttribute::type, ProductAttribute::value)
                .containsExactlyInAnyOrder(tuple("Origem", "Brasil"), tuple("Peso", "50g"));
    }

    @Test
    void product_atributosDeRaizEDeVariacaoNaoSeMisturamNoBanco() {
        // Tabelas diferentes (product_root_attribute x product_attribute); o teste prova que uma
        // não vaza para a outra ao recarregar.
        productRepository.save(Product.create("RT-ATR-002", "Essência", "essencia",
                List.of(ProductVariant.create("RT-ATR-002-A", List.of(new ProductAttribute("Sabor", "Menta")))),
                Pricing.empty(), ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil"))));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-ATR-002").orElseThrow();

        assertThat(reloaded.attributes()).extracting(ProductAttribute::type).containsExactly("Origem");
        assertThat(reloaded.variants()).hasSize(1);
        assertThat(reloaded.variants().get(0).attributes())
                .extracting(ProductAttribute::type).containsExactly("Sabor");
    }

    @Test
    void product_semAtributosDeRaizVoltaListaVaziaENuncaNull() {
        productRepository.save(Product.create("RT-ATR-003", "Carvão", "carvao", List.of()));
        flushAndClear();

        assertThat(productRepository.findBySku("RT-ATR-003").orElseThrow().attributes())
                .isNotNull().isEmpty();
    }

    @Test
    void product_atributosDeRaizAceitamTipoRepetidoComValoresDiferentes() {
        // Não há UNIQUE em (product_id, attr_type): um blend pode ter dois sabores.
        productRepository.save(Product.create("RT-ATR-004", "Blend", "essencia", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Sabor", "Menta"), new ProductAttribute("Sabor", "Limão"))));
        flushAndClear();

        assertThat(productRepository.findBySku("RT-ATR-004").orElseThrow().attributes())
                .extracting(ProductAttribute::value)
                .containsExactlyInAnyOrder("Menta", "Limão");
    }

    @Test
    void product_substituicaoDaColecaoDeAtributosApagaOsAnteriores() {
        productRepository.save(Product.create("RT-ATR-005", "Essência", "essencia", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil"))));
        flushAndClear();

        Product reloaded = productRepository.findBySku("RT-ATR-005").orElseThrow();
        productRepository.save(reloaded.withAttributes(List.of(new ProductAttribute("Peso", "50g"))));
        flushAndClear();

        assertThat(productRepository.findBySku("RT-ATR-005").orElseThrow().attributes())
                .extracting(ProductAttribute::type).containsExactly("Peso");
    }

    // ── Listagem filtrada do catálogo ────────────────────────────────────────
    //
    // Todos os produtos destes testes usam o prefixo FLT- e categorias/marcas próprias, para as
    // asserções serem exatas apesar de o banco ser compartilhado com os demais testes da classe.

    private void givenCatalogoParaFiltro() {
        productRepository.save(Product.create("FLT-001", "Essência Menta", "flt-essencia", List.of(),
                new Pricing(new BigDecimal("10.00"), null, new BigDecimal("30.00"), null, null),
                ProductType.SIMPLES, false, "flt-zomo"));
        productRepository.save(Product.create("FLT-002", "Essência Melancia", "flt-essencia", List.of(),
                new Pricing(new BigDecimal("10.00"), null, new BigDecimal("10.00"), null, null),
                ProductType.SIMPLES, false, "flt-adalya"));
        productRepository.save(Product.create("FLT-003", "Narguilé Grande", "flt-narguile", List.of(),
                new Pricing(new BigDecimal("10.00"), null, new BigDecimal("20.00"), null, null),
                ProductType.SIMPLES, false, "flt-zomo"));
        Product inativo = productRepository.save(Product.create("FLT-004", "Carvão Menta", "flt-carvao", List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, "flt-zomo"));
        productRepository.save(inativo.withActive(false));
        flushAndClear();
    }

    private List<String> skus(PageResult<Product> page) {
        return page.content().stream().map(Product::sku).toList();
    }

    private PageResult<Product> filtrar(ProductFilter filter) {
        return productRepository.findAll(0, 50, filter, ProductSortField.ID, SortDirection.ASC);
    }

    @Test
    void findAll_semFiltro_devolveTudoComoAntes() {
        givenCatalogoParaFiltro();

        PageResult<Product> page = filtrar(ProductFilter.EMPTY);

        assertThat(skus(page)).contains("FLT-001", "FLT-002", "FLT-003", "FLT-004");
    }

    @Test
    void findAll_buscaPorTrechoDoNomeIgnorandoCaixa() {
        givenCatalogoParaFiltro();

        assertThat(skus(filtrar(new ProductFilter("MENTA", null, null, null))))
                .containsExactlyInAnyOrder("FLT-001", "FLT-004");
    }

    @Test
    void findAll_buscaCasaTambemPorTrechoDoSku() {
        // É um campo de busca único na tela do admin: o operador digita nome ou código.
        givenCatalogoParaFiltro();

        assertThat(skus(filtrar(new ProductFilter("flt-00", null, null, null))))
                .contains("FLT-001", "FLT-002", "FLT-003", "FLT-004");
    }

    @Test
    void findAll_filtraPorCategoriaComIgualdadeExata() {
        givenCatalogoParaFiltro();

        // "flt-essencia" não pode arrastar "flt-narguile" nem casar por prefixo.
        assertThat(skus(filtrar(new ProductFilter(null, "FLT-ESSENCIA", null, null))))
                .containsExactlyInAnyOrder("FLT-001", "FLT-002");
    }

    @Test
    void findAll_filtraPorMarca() {
        givenCatalogoParaFiltro();

        assertThat(skus(filtrar(new ProductFilter(null, null, "flt-adalya", null))))
                .containsExactly("FLT-002");
    }

    @Test
    void findAll_filtraPorAtivoEPorInativo() {
        givenCatalogoParaFiltro();

        assertThat(skus(filtrar(new ProductFilter("flt-00", null, null, true))))
                .containsExactlyInAnyOrder("FLT-001", "FLT-002", "FLT-003");
        // active=false é filtro de verdade, não ausência de filtro.
        assertThat(skus(filtrar(new ProductFilter("flt-00", null, null, false))))
                .containsExactly("FLT-004");
    }

    @Test
    void findAll_filtraPorType() {
        productRepository.save(Product.create("FLT-TYPE-SIMPLES", "Componente", "flt-type", List.of()));
        productRepository.save(Product.create("FLT-TYPE-KIT", "Kit", "flt-type", List.of(),
                Pricing.empty(), ProductType.KIT));
        flushAndClear();

        assertThat(skus(filtrar(new ProductFilter(null, "flt-type", null, null, ProductType.KIT))))
                .containsExactly("FLT-TYPE-KIT");
        assertThat(skus(filtrar(new ProductFilter(null, "flt-type", null, null, ProductType.SIMPLES))))
                .containsExactly("FLT-TYPE-SIMPLES");
    }

    @Test
    void findAll_filtraPorStatus() {
        productRepository.save(Product.create("FLT-STATUS-ATIVO", "Produto", "flt-status", List.of()));
        productRepository.save(Product.create("FLT-STATUS-RASCUNHO", "Rascunho", "flt-status", List.of())
                .withStatus(ProductStatus.RASCUNHO));
        flushAndClear();

        assertThat(skus(filtrar(new ProductFilter(null, "flt-status", null, null, null, null, ProductStatus.RASCUNHO))))
                .containsExactly("FLT-STATUS-RASCUNHO");
        assertThat(skus(filtrar(new ProductFilter(null, "flt-status", null, null, null, null, ProductStatus.ATIVO))))
                .containsExactly("FLT-STATUS-ATIVO");
    }

    @Test
    void findAll_filtraPorProdutoDeSessao() {
        productRepository.save(Product.create("FLT-SESSAO-SIM", "Essência", "flt-sessao", List.of())
                .withSessionProduct(true));
        productRepository.save(Product.create("FLT-SESSAO-NAO", "Carvão", "flt-sessao", List.of()));
        flushAndClear();

        assertThat(skus(filtrar(new ProductFilter(null, "flt-sessao", null, null, null, null, null, true))))
                .containsExactly("FLT-SESSAO-SIM");
        assertThat(skus(filtrar(new ProductFilter(null, "flt-sessao", null, null, null, null, null, false))))
                .containsExactly("FLT-SESSAO-NAO");
    }

    @Test
    void countByStatus_contaSoOsRascunhos() {
        long antes = productRepository.countByStatus(ProductStatus.RASCUNHO);
        productRepository.save(Product.create("CNT-STATUS-001", "Rascunho", "cnt-status", List.of())
                .withStatus(ProductStatus.RASCUNHO));
        productRepository.save(Product.create("CNT-STATUS-002", "Ativo", "cnt-status", List.of()));
        flushAndClear();

        assertThat(productRepository.countByStatus(ProductStatus.RASCUNHO)).isEqualTo(antes + 1);
    }

    @Test
    void findAll_combinaOsFiltrosComE() {
        givenCatalogoParaFiltro();

        assertThat(skus(filtrar(new ProductFilter("essência", "flt-essencia", "flt-zomo", true))))
                .containsExactly("FLT-001");
    }

    @Test
    void findAll_filtroSemResultadoDevolvePaginaVaziaComTotalZero() {
        givenCatalogoParaFiltro();

        PageResult<Product> page = filtrar(new ProductFilter("nao-existe-nada-assim", null, null, null));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }

    @Test
    void findAll_totalElementsRefleteOFiltroENaoOCatalogoInteiro() {
        // É este número que alimenta a paginação do admin — se viesse do catálogo inteiro, a tela
        // ofereceria páginas que não existem.
        givenCatalogoParaFiltro();

        assertThat(filtrar(new ProductFilter(null, "flt-essencia", null, null)).totalElements())
                .isEqualTo(2L);
    }

    @Test
    void findAll_ordenaPorNomeNosDoisSentidos() {
        givenCatalogoParaFiltro();
        ProductFilter soFlt = new ProductFilter(null, null, "flt-zomo", null);

        assertThat(skus(productRepository.findAll(0, 50, soFlt, ProductSortField.NAME, SortDirection.ASC)))
                .containsExactly("FLT-004", "FLT-001", "FLT-003"); // Carvão, Essência, Narguilé
        assertThat(skus(productRepository.findAll(0, 50, soFlt, ProductSortField.NAME, SortDirection.DESC)))
                .containsExactly("FLT-003", "FLT-001", "FLT-004");
    }

    @Test
    void findAll_ordenaPorPrecoDeVenda() {
        givenCatalogoParaFiltro();
        ProductFilter comPreco = new ProductFilter(null, null, null, true);

        List<String> ordenados = skus(productRepository.findAll(0, 50, comPreco,
                ProductSortField.SALE_PRICE, SortDirection.ASC));

        // 10.00 (FLT-002) < 20.00 (FLT-003) < 30.00 (FLT-001)
        assertThat(ordenados).containsSubsequence("FLT-002", "FLT-003", "FLT-001");
    }

    @Test
    void findAll_ordenacaoPorCampoNaoUnicoNaoRepeteNemPulaLinha() {
        // EST-C012 aplicado ao catálogo: os quatro produtos abaixo têm o MESMO nome, então
        // ordenar só por nome deixaria a ordem indefinida entre páginas e o banco poderia
        // devolver a mesma linha duas vezes. O desempate por id é o que torna a paginação estável.
        for (int i = 1; i <= 4; i++) {
            productRepository.save(Product.create("DUP-00" + i, "Nome Repetido", "dup-cat", List.of(),
                    Pricing.empty(), ProductType.SIMPLES, false, "dup-marca"));
        }
        flushAndClear();

        ProductFilter filter = new ProductFilter(null, "dup-cat", null, null);
        List<String> vistos = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            vistos.addAll(skus(productRepository.findAll(page, 1, filter,
                    ProductSortField.NAME, SortDirection.ASC)));
        }

        assertThat(vistos).containsExactly("DUP-001", "DUP-002", "DUP-003", "DUP-004");
    }

    @Test
    void findAll_ordenacaoNaoEDesfeitaPeloFetchDasVariacoes() {
        // O fetch das variações tem ORDER BY p.id fixo; a ordem pedida precisa sobreviver a ele,
        // senão a página vem com o conteúdo certo na ordem errada.
        productRepository.save(Product.create("ORD-001", "Zebra", "ord-cat",
                List.of(ProductVariant.create("ORD-001-A", List.of(new ProductAttribute("cor", "azul"))))));
        productRepository.save(Product.create("ORD-002", "Abacaxi", "ord-cat",
                List.of(ProductVariant.create("ORD-002-A", List.of(new ProductAttribute("cor", "verde"))))));
        flushAndClear();

        PageResult<Product> page = productRepository.findAll(0, 50,
                new ProductFilter(null, "ord-cat", null, null), ProductSortField.NAME, SortDirection.ASC);

        assertThat(skus(page)).containsExactly("ORD-002", "ORD-001");
        assertThat(page.content().get(0).variants()).hasSize(1);
        assertThat(page.content().get(0).variants().get(0).attributes())
                .extracting(ProductAttribute::value).containsExactly("verde");
    }

    @Test
    void warehouse_roundTripEFindByCode() {
        Warehouse saved = givenWarehouse("WH-RT");
        flushAndClear();

        assertThat(saved.id()).isNotNull();
        assertThat(warehouseRepository.findByCode("WH-RT")).isPresent()
                .get().satisfies(w -> assertThat(w.type()).isEqualTo(WarehouseType.LOJA_FISICA));
        assertThat(warehouseRepository.findByCode("WH-INEXISTENTE")).isEmpty();
        assertThat(warehouseRepository.findAll(0, 100).content())
                .extracting(Warehouse::code).contains("WH-RT");
    }

    /**
     * EST-C005: a listagem de depósitos passou a ser paginada. A ordenação é por {@code id}
     * (BIGSERIAL monotônico, chave única) para a paginação ser estável — {@code code} também
     * seria único, mas {@code id} reflete a ordem de criação, que é a que o operador espera.
     */
    @Test
    void warehouse_paginaOrdenadoPorId() {
        Warehouse primeiro = givenWarehouse("WH-PAG-A");
        Warehouse segundo = givenWarehouse("WH-PAG-B");
        flushAndClear();

        PageResult<Warehouse> pagina = warehouseRepository.findAll(0, 1);

        assertThat(pagina.content()).hasSize(1);
        assertThat(pagina.size()).isEqualTo(1);
        assertThat(pagina.totalElements()).isGreaterThanOrEqualTo(2);
        assertThat(pagina.totalPages()).isGreaterThanOrEqualTo(2);

        // Varre todas as páginas: os dois depósitos aparecem uma vez cada, na ordem de criação.
        List<Long> ids = new ArrayList<>();
        for (int page = 0; page < pagina.totalPages(); page++) {
            warehouseRepository.findAll(page, 1).content().forEach(w -> ids.add(w.id()));
        }
        assertThat(ids).containsSequence(primeiro.id(), segundo.id());
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void stockBalance_persisteSaldoEAvancaVersionAoAtualizar() {
        Warehouse warehouse = givenWarehouse("WH-VER");

        StockBalance inserted = stockBalanceRepository.save(
                StockBalance.zero("VER-001", warehouse.id()).apply(MovementType.ENTRADA, new BigDecimal("10.000")));
        flushAndClear();

        assertThat(inserted.id()).isNotNull();

        StockBalance reread = stockBalanceRepository.findBySkuAndWarehouseId("VER-001", warehouse.id()).orElseThrow();
        assertThat(reread.quantity()).isEqualByComparingTo("10.000");

        stockBalanceRepository.save(reread.apply(MovementType.SAIDA, new BigDecimal("4.000")));
        flushAndClear();

        StockBalance afterUpdate = stockBalanceRepository.findBySkuAndWarehouseId("VER-001", warehouse.id()).orElseThrow();
        assertThat(afterUpdate.quantity()).isEqualByComparingTo("6.000");
        assertThat(afterUpdate.version())
                .as("o @Version tem que avançar a cada escrita — é o que detecta o conflito concorrente")
                .isGreaterThan(reread.version());
    }

    @Test
    void stockMovement_paginaDoMaisRecenteParaOMaisAntigo() {
        // Grava três movimentos em sequência imediata, como faz uma venda com três itens: o
        // created_at sai idêntico e a ordenação tem que se apoiar no desempate por id, senão a
        // ordem vira arbitrária e a paginação fica instável.
        Warehouse warehouse = givenWarehouse("WH-MOV");
        stockMovementRepository.save(StockMovement.create("MOV-001", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("10.000"), "Primeira", "gerente"));
        stockMovementRepository.save(StockMovement.create("MOV-001", warehouse.id(), MovementType.SAIDA,
                new BigDecimal("2.000"), "Segunda", "gerente"));
        stockMovementRepository.save(StockMovement.create("MOV-001", warehouse.id(), MovementType.SAIDA,
                new BigDecimal("1.000"), "Terceira", "gerente"));
        flushAndClear();

        PageResult<StockMovement> page =
                stockMovementRepository.findBySkuAndWarehouseId("MOV-001", warehouse.id(), 0, 10);

        assertThat(page.content()).hasSize(3);
        assertThat(page.content()).extracting(StockMovement::reason)
                .as("mais recente primeiro, com desempate determinístico por id")
                .containsExactly("Terceira", "Segunda", "Primeira");
        assertThat(page.content().get(0).username()).isEqualTo("gerente");
        assertThat(page.content().get(0).createdAt()).isNotNull();
    }

    @Test
    void stockMovement_paginacaoNaoRepeteNemPulaLinhaComCreatedAtIgual() {
        // O risco real do EST-C012: com chave de ordenação não-única, a mesma linha pode voltar em
        // duas páginas ou não aparecer em nenhuma.
        Warehouse warehouse = givenWarehouse("WH-PAG");
        for (int i = 1; i <= 5; i++) {
            stockMovementRepository.save(StockMovement.create("PAG-001", warehouse.id(), MovementType.ENTRADA,
                    new BigDecimal("1.000"), "Movimento " + i, "gerente"));
        }
        flushAndClear();

        List<String> primeiraPagina = stockMovementRepository
                .findBySkuAndWarehouseId("PAG-001", warehouse.id(), 0, 2)
                .content().stream().map(StockMovement::reason).toList();
        List<String> segundaPagina = stockMovementRepository
                .findBySkuAndWarehouseId("PAG-001", warehouse.id(), 1, 2)
                .content().stream().map(StockMovement::reason).toList();
        List<String> terceiraPagina = stockMovementRepository
                .findBySkuAndWarehouseId("PAG-001", warehouse.id(), 2, 2)
                .content().stream().map(StockMovement::reason).toList();

        assertThat(primeiraPagina).hasSize(2);
        assertThat(segundaPagina).hasSize(2);
        assertThat(terceiraPagina).hasSize(1);
        assertThat(primeiraPagina).doesNotContainAnyElementsOf(segundaPagina);
        assertThat(terceiraPagina).doesNotContainAnyElementsOf(primeiraPagina)
                .doesNotContainAnyElementsOf(segundaPagina);
        assertThat(Stream.of(primeiraPagina, segundaPagina, terceiraPagina).flatMap(List::stream).toList())
                .as("as três páginas juntas têm que cobrir os 5 movimentos, sem repetir nem pular")
                .containsExactly("Movimento 5", "Movimento 4", "Movimento 3", "Movimento 2", "Movimento 1");
    }

    @Test
    void stockMovement_paginaVaziaQuandoParNuncaMovimentado() {
        Warehouse warehouse = givenWarehouse("WH-EMPTY");
        flushAndClear();

        PageResult<StockMovement> page =
                stockMovementRepository.findBySkuAndWarehouseId("SEM-USO", warehouse.id(), 0, 10);

        assertThat(page.content()).isEmpty();
        assertThat(page.totalElements()).isZero();
    }

    @Test
    void stockMovement_roundTrip_preservaLotCode() {
        Warehouse warehouse = givenWarehouse("WH-LOT-MOV");
        stockMovementRepository.save(StockMovement.create("LOT-MOV-001", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("5.000"), "Recebimento", "gerente", "LOTE-A"));
        flushAndClear();

        PageResult<StockMovement> page =
                stockMovementRepository.findBySkuAndWarehouseId("LOT-MOV-001", warehouse.id(), 0, 10);

        assertThat(page.content()).singleElement()
                .satisfies(m -> assertThat(m.lotCode()).isEqualTo("LOTE-A"));
    }

    // ---------- EST-F008 — lote e validade ----------

    @Test
    void stockLot_roundTrip_persisteEReconstitui() {
        Warehouse warehouse = givenWarehouse("WH-LOT");
        StockLot inserted = stockLotRepository.save(
                StockLot.create("LOT-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 3, 1))
                        .receive(new BigDecimal("10.000")));
        flushAndClear();

        assertThat(inserted.id()).isNotNull();

        StockLot reread = stockLotRepository
                .findBySkuAndWarehouseIdAndLotCode("LOT-001", warehouse.id(), "LOTE-A").orElseThrow();
        assertThat(reread.quantity()).isEqualByComparingTo("10.000");
        assertThat(reread.expiryDate()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(reread.alertedAt()).isNull();

        stockLotRepository.save(reread.consume(new BigDecimal("4.000")));
        flushAndClear();

        StockLot afterUpdate = stockLotRepository
                .findBySkuAndWarehouseIdAndLotCode("LOT-001", warehouse.id(), "LOTE-A").orElseThrow();
        assertThat(afterUpdate.quantity()).isEqualByComparingTo("6.000");
        assertThat(afterUpdate.version())
                .as("o @Version tem que avançar a cada escrita, mesmo padrão de stock_balance")
                .isGreaterThan(reread.version());
    }

    @Test
    void stockLot_findBySkuAndWarehouseId_ordenaPorFefo() {
        Warehouse warehouse = givenWarehouse("WH-FEFO");
        // Grava fora de ordem de vencimento de propósito — a consulta é quem tem que ordenar.
        stockLotRepository.save(StockLot.create("FEFO-001", warehouse.id(), "LOTE-C", LocalDate.of(2027, 6, 1))
                .receive(BigDecimal.ONE));
        stockLotRepository.save(StockLot.create("FEFO-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(BigDecimal.ONE));
        stockLotRepository.save(StockLot.create("FEFO-001", warehouse.id(), "LOTE-B", LocalDate.of(2027, 3, 1))
                .receive(BigDecimal.ONE));
        flushAndClear();

        List<StockLot> lots = stockLotRepository.findBySkuAndWarehouseId("FEFO-001", warehouse.id());

        assertThat(lots).extracting(StockLot::lotCode)
                .as("do que vence primeiro para o que vence por último")
                .containsExactly("LOTE-A", "LOTE-B", "LOTE-C");
    }

    @Test
    void stockLot_findExpiringSoon_respeitaCutoffQuantityEAlertedAt() {
        Warehouse warehouse = givenWarehouse("WH-EXP");
        stockLotRepository.save(StockLot.create("EXP-001", warehouse.id(), "LOTE-VENCIDO", LocalDate.of(2026, 1, 1))
                .receive(BigDecimal.TEN));
        stockLotRepository.save(StockLot.create("EXP-001", warehouse.id(), "LOTE-LONGE", LocalDate.of(2030, 1, 1))
                .receive(BigDecimal.TEN));
        StockLot semSaldo = stockLotRepository.save(
                StockLot.create("EXP-001", warehouse.id(), "LOTE-ZERADO", LocalDate.of(2026, 1, 1))
                        .receive(BigDecimal.TEN));
        stockLotRepository.save(semSaldo.consume(BigDecimal.TEN));
        StockLot jaAlertado = stockLotRepository.save(
                StockLot.create("EXP-001", warehouse.id(), "LOTE-ALERTADO", LocalDate.of(2026, 1, 1))
                        .receive(BigDecimal.TEN));
        stockLotRepository.save(jaAlertado.alerted());
        flushAndClear();

        List<StockLot> expiring = stockLotRepository.findExpiringSoon(LocalDate.of(2026, 12, 31), 10);

        assertThat(expiring).extracting(StockLot::lotCode)
                .as("vencido entra, sem saldo e já alertado ficam de fora, e o que vence longe não entra")
                .containsExactly("LOTE-VENCIDO");
    }

    /**
     * Caso de borda do cutoff: no teste acima, todo lote está bem antes ou bem depois da data de
     * corte — nenhum bate em cheio. Aqui o {@code expiryDate} é EXATAMENTE igual ao cutoff, o que
     * prova se a query usa {@code <=} (inclusivo) ou {@code <} (exclusivo).
     */
    @Test
    void stockLot_findExpiringSoon_incluiLoteComExpiryDateIgualAoCutoff() {
        Warehouse warehouse = givenWarehouse("WH-EXP-EDGE");
        LocalDate cutoff = LocalDate.of(2026, 12, 31);
        stockLotRepository.save(StockLot.create("EXP-EDGE-001", warehouse.id(), "LOTE-NO-CUTOFF", cutoff)
                .receive(BigDecimal.TEN));
        flushAndClear();

        List<StockLot> expiring = stockLotRepository.findExpiringSoon(cutoff, 10);

        assertThat(expiring).extracting(StockLot::lotCode)
                .as("expiryDate igual ao cutoff entra no resultado: a comparação é <=, não <")
                .containsExactly("LOTE-NO-CUTOFF");
    }

    @Test
    void reorderPoint_upsertReaproveitaOId() {
        Warehouse warehouse = givenWarehouse("WH-RP");
        ReorderPoint created = reorderPointRepository.save(
                new ReorderPoint(null, "RP-001", warehouse.id(), new BigDecimal("5.000")));
        flushAndClear();

        assertThat(created.id()).isNotNull();

        reorderPointRepository.save(new ReorderPoint(created.id(), "RP-001", warehouse.id(), new BigDecimal("12.000")));
        flushAndClear();

        assertThat(reorderPointRepository.findBySkuAndWarehouseId("RP-001", warehouse.id()))
                .get().satisfies(rp -> {
                    assertThat(rp.id()).as("upsert não pode criar uma segunda linha para o mesmo par")
                            .isEqualTo(created.id());
                    assertThat(rp.minQuantity()).isEqualByComparingTo("12.000");
                });
    }

    // ------------------------------------------------------------------------------------------
    // EST-F006 — persistência do balanço de inventário.
    // ------------------------------------------------------------------------------------------

    @Test
    void stockCount_roundTripPreservaItensEDivergencias() {
        Warehouse warehouse = givenWarehouse("WH-CNT-RT");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-001", new BigDecimal("8.000"))
                .withCountedItem("CNT-002", new BigDecimal("0.000")));
        flushAndClear();

        assertThat(aberta.id()).isNotNull();
        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(StockCountStatus.ABERTA);
            assertThat(c.username()).isEqualTo("gerente");
            assertThat(c.closedAt()).isNull();
            assertThat(c.items()).hasSize(2);
            assertThat(c.items()).allSatisfy(item -> {
                assertThat(item.id()).isNotNull();
                assertThat(item.expectedQuantity()).as("aberta ainda não confrontou").isNull();
            });
        });
    }

    @Test
    void stockCount_fechamentoPersisteExpectedEDifference() {
        Warehouse warehouse = givenWarehouse("WH-CNT-CLOSE");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-010", new BigDecimal("8.000")));
        flushAndClear();

        StockCount carregada = stockCountRepository.findById(aberta.id()).orElseThrow();
        List<StockCountItem> reconciliados = carregada.items().stream()
                .map(i -> i.reconciledWith(new BigDecimal("10.000")))
                .toList();
        stockCountRepository.save(carregada.withReconciledItems(reconciliados).closed());
        flushAndClear();

        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c -> {
            assertThat(c.status()).isEqualTo(StockCountStatus.FECHADA);
            assertThat(c.closedAt()).isNotNull();
            assertThat(c.items()).singleElement().satisfies(item -> {
                assertThat(item.expectedQuantity()).isEqualByComparingTo("10.000");
                assertThat(item.difference()).isEqualByComparingTo("-2.000");
            });
        });
    }

    /** A regra de um balanço aberto por depósito depende desta consulta. */
    @Test
    void stockCount_findOpenByWarehouseId_soEnxergaAAberta() {
        Warehouse warehouse = givenWarehouse("WH-CNT-OPEN");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente"));
        flushAndClear();

        assertThat(stockCountRepository.findOpenByWarehouseId(warehouse.id()))
                .get().satisfies(c -> assertThat(c.id()).isEqualTo(aberta.id()));

        stockCountRepository.save(stockCountRepository.findById(aberta.id()).orElseThrow().closed());
        flushAndClear();

        assertThat(stockCountRepository.findOpenByWarehouseId(warehouse.id()))
                .as("fechada não conta como aberta").isEmpty();
    }

    @Test
    void stockCount_recontagemNaoDuplicaLinhaDoMesmoSku() {
        Warehouse warehouse = givenWarehouse("WH-CNT-DUP");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-020", new BigDecimal("3.000")));
        flushAndClear();

        StockCount carregada = stockCountRepository.findById(aberta.id()).orElseThrow();
        stockCountRepository.save(carregada.withCountedItem("CNT-020", new BigDecimal("9.000")));
        flushAndClear();

        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c ->
                assertThat(c.items()).as("uk_stock_count_item_count_sku").singleElement()
                        .satisfies(item -> assertThat(item.countedQuantity()).isEqualByComparingTo("9.000")));
    }

    /**
     * EST-F008: lote diferente do mesmo SKU é linha nova; recontar o mesmo lote sobrescreve —
     * espelha {@code stockCount_recontagemNaoDuplicaLinhaDoMesmoSku}, mas por
     * {@code (sku, lotCode)} em vez de só {@code sku}.
     */
    @Test
    void stockCount_recontagemPorLote_naoDuplicaLinhaDoMesmoLoteEPreservaLotesDiferentes() {
        Warehouse warehouse = givenWarehouse("WH-CNT-LOT");
        StockCount aberta = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente")
                .withCountedItem("CNT-LOT-001", new BigDecimal("3.000"), "LOTE-A")
                .withCountedItem("CNT-LOT-001", new BigDecimal("5.000"), "LOTE-B"));
        flushAndClear();

        StockCount carregada = stockCountRepository.findById(aberta.id()).orElseThrow();
        stockCountRepository.save(carregada.withCountedItem("CNT-LOT-001", new BigDecimal("9.000"), "LOTE-A"));
        flushAndClear();

        assertThat(stockCountRepository.findById(aberta.id())).get().satisfies(c ->
                assertThat(c.items()).extracting(StockCountItem::lotCode, StockCountItem::countedQuantity)
                        .as("LOTE-A recontado sobrescreve, LOTE-B continua intacto — nunca 3 linhas")
                        .containsExactlyInAnyOrder(
                                tuple("LOTE-A", new BigDecimal("9.000")),
                                tuple("LOTE-B", new BigDecimal("5.000"))));
    }

    @Test
    void stockCount_listaDoMaisRecenteParaOMaisAntigo() {
        Warehouse warehouse = givenWarehouse("WH-CNT-PAG");
        for (int i = 0; i < 3; i++) {
            StockCount c = stockCountRepository.save(StockCount.open(warehouse.id(), "gerente"));
            stockCountRepository.save(stockCountRepository.findById(c.id()).orElseThrow().closed());
        }
        flushAndClear();

        PageResult<StockCount> page = stockCountRepository.findByWarehouseId(warehouse.id(), 0, 2);

        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.content().get(0).id())
                .as("mais recente primeiro, com desempate por id").isGreaterThan(page.content().get(1).id());
    }

    // ------------------------------------------------------------------------------------------
    // EST-C011 — diagnóstico de SKU órfão.
    //
    // É o único teste que exercita a query nativa de StockIntegrityJpaRepository; service e
    // controller só delegam. As asserções filtram pelos SKUs criados aqui em vez de comparar a
    // página inteira, porque a consulta varre a base toda e não pode assumir que ela está vazia.
    // ------------------------------------------------------------------------------------------

    /** Varre todas as páginas e devolve só as linhas cujo SKU começa com o prefixo do teste. */
    private List<OrphanSku> orphansWithPrefix(String prefix) {
        PageResult<OrphanSku> first = stockIntegrityRepository.findOrphanSkus(0, 100);
        List<OrphanSku> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) {
            all.addAll(stockIntegrityRepository.findOrphanSkus(page, 100).content());
        }
        return all.stream().filter(o -> o.sku().startsWith(prefix)).toList();
    }

    @Test
    void orphanSkus_naoAcusaSkuPaiNemSkuDeVariacaoCadastrados() {
        Warehouse warehouse = givenWarehouse("WH-ORF-OK");
        productRepository.save(Product.create("ORF-OK-001", "Essência", "essencia",
                List.of(ProductVariant.create("ORF-OK-001-UVA", List.of(new ProductAttribute("sabor", "uva"))))));
        // Movimenta os dois: o SKU pai e o de variação. Se o anti-join olhasse só product,
        // toda variação com saldo apareceria como órfã — o mesmo furo que o EST-C002 fechou.
        stockBalanceRepository.save(StockBalance.of(null, "ORF-OK-001", warehouse.id(), new BigDecimal("5.000"), 0L));
        stockBalanceRepository.save(
                StockBalance.of(null, "ORF-OK-001-UVA", warehouse.id(), new BigDecimal("3.000"), 0L));
        stockMovementRepository.save(StockMovement.create("ORF-OK-001-UVA", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("3.000"), "Recebimento", "gerente"));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-OK-")).isEmpty();
    }

    @Test
    void orphanSkus_acusaSkuForaDoCatalogoComSaldo() {
        Warehouse warehouse = givenWarehouse("WH-ORF-BAL");
        stockBalanceRepository.save(
                StockBalance.of(null, "ORF-BAL-999", warehouse.id(), new BigDecimal("7.500"), 0L));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-BAL-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.sku()).isEqualTo("ORF-BAL-999");
            assertThat(orphan.warehouseCode()).isEqualTo("WH-ORF-BAL");
            assertThat(orphan.quantity()).isEqualByComparingTo("7.500");
            assertThat(orphan.movementCount()).isZero();
            assertThat(orphan.hasReorderPoint()).isFalse();
            assertThat(orphan.lastMovementAt()).as("nunca movimentado").isNull();
        });
    }

    /**
     * Caso mais provável na prática: o ledger é imutável, então sobra movimento sem linha de saldo.
     *
     * <p>O {@code lastMovementAt} é conferido contra o que o próprio
     * {@code StockMovementRepository} devolve para o movimento mais recente, e não contra um
     * {@code Instant} literal. A query de diagnóstico é nativa e recebe o tipo cru do driver para
     * {@code created_at}, enquanto o ledger passa pelo mapeamento de entidade do Hibernate:
     * comparar os dois é justamente o que prova que a conversão do adapter não desloca o
     * instante.</p>
     */
    @Test
    void orphanSkus_acusaSkuSoComMovimentacoesEDevolveSaldoZero() {
        Warehouse warehouse = givenWarehouse("WH-ORF-MOV");
        stockMovementRepository.save(StockMovement.of(null, "ORF-MOV-999", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("2.000"), "Entrada antiga", "gerente", Instant.parse("2026-01-10T08:00:00Z")));
        stockMovementRepository.save(StockMovement.of(null, "ORF-MOV-999", warehouse.id(), MovementType.SAIDA,
                new BigDecimal("2.000"), "Saída recente", "gerente", Instant.parse("2026-03-20T15:30:00Z")));
        flushAndClear();

        // O ledger já ordena do mais recente para o mais antigo (EST-C012).
        Instant maisRecenteNoLedger = stockMovementRepository
                .findBySkuAndWarehouseId("ORF-MOV-999", warehouse.id(), 0, 1)
                .content().getFirst().createdAt();

        assertThat(orphansWithPrefix("ORF-MOV-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.quantity()).as("sem linha de saldo, o COALESCE devolve zero")
                    .isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(orphan.movementCount()).isEqualTo(2L);
            assertThat(orphan.lastMovementAt()).as("MAX(created_at), não o primeiro movimento")
                    .isEqualTo(maisRecenteNoLedger);
        });
    }

    @Test
    void orphanSkus_acusaSkuSoComPontoDeReposicao() {
        Warehouse warehouse = givenWarehouse("WH-ORF-RP");
        reorderPointRepository.save(new ReorderPoint(null, "ORF-RP-999", warehouse.id(), new BigDecimal("10.000")));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-RP-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.hasReorderPoint()).isTrue();
            assertThat(orphan.quantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(orphan.movementCount()).isZero();
            assertThat(orphan.lastMovementAt()).isNull();
        });
    }

    @Test
    void orphanSkus_umaLinhaPorDeposito() {
        Warehouse loja = givenWarehouse("WH-ORF-A");
        Warehouse ecommerce = givenWarehouse("WH-ORF-B");
        stockBalanceRepository.save(StockBalance.of(null, "ORF-DUP-999", loja.id(), new BigDecimal("4.000"), 0L));
        stockBalanceRepository.save(StockBalance.of(null, "ORF-DUP-999", ecommerce.id(), new BigDecimal("6.000"), 0L));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-DUP-"))
                .as("o UNION não pode colapsar os depósitos: cada um é uma decisão separada")
                .hasSize(2)
                .extracting(OrphanSku::warehouseCode)
                .containsExactly("WH-ORF-A", "WH-ORF-B");
    }

    /**
     * O mesmo par SKU/depósito nas três tabelas tem que virar uma linha só, não três — é para
     * isso que a origem é {@code UNION} e não {@code UNION ALL}.
     */
    @Test
    void orphanSkus_naoDuplicaQuandoOParEstaNasTresTabelas() {
        Warehouse warehouse = givenWarehouse("WH-ORF-ALL");
        stockBalanceRepository.save(StockBalance.of(null, "ORF-ALL-999", warehouse.id(), new BigDecimal("9.000"), 0L));
        stockMovementRepository.save(StockMovement.create("ORF-ALL-999", warehouse.id(), MovementType.ENTRADA,
                new BigDecimal("9.000"), "Entrada", "gerente"));
        reorderPointRepository.save(new ReorderPoint(null, "ORF-ALL-999", warehouse.id(), new BigDecimal("2.000")));
        flushAndClear();

        assertThat(orphansWithPrefix("ORF-ALL-")).singleElement().satisfies(orphan -> {
            assertThat(orphan.quantity()).isEqualByComparingTo("9.000");
            assertThat(orphan.movementCount()).isEqualTo(1L);
            assertThat(orphan.hasReorderPoint()).isTrue();
            assertThat(orphan.lastMovementAt()).isNotNull();
        });
    }

    /**
     * Paginação estável: varrendo página a página com {@code size = 1}, cada órfão aparece
     * exatamente uma vez. A ordenação é por {@code (sku, warehouse_code)} justamente porque chave
     * não-única deixa o banco livre para repetir ou pular linha entre páginas (EST-C012).
     */
    @Test
    void orphanSkus_paginacaoNaoRepeteNemPulaLinha() {
        Warehouse warehouse = givenWarehouse("WH-ORF-PAG");
        Stream.of("ORF-PAG-001", "ORF-PAG-002", "ORF-PAG-003").forEach(sku ->
                stockBalanceRepository.save(StockBalance.of(null, sku, warehouse.id(), new BigDecimal("1.000"), 0L)));
        flushAndClear();

        PageResult<OrphanSku> primeira = stockIntegrityRepository.findOrphanSkus(0, 1);
        assertThat(primeira.size()).isEqualTo(1);
        assertThat(primeira.totalElements()).isGreaterThanOrEqualTo(3);

        List<String> varridos = new ArrayList<>();
        for (int page = 0; page < primeira.totalPages(); page++) {
            PageResult<OrphanSku> atual = stockIntegrityRepository.findOrphanSkus(page, 1);
            assertThat(atual.content()).as("página %d", page).hasSize(1);
            varridos.add(atual.content().getFirst().sku());
        }

        assertThat(varridos.stream().filter(sku -> sku.startsWith("ORF-PAG-")).toList())
                .as("cada órfão exatamente uma vez, em ordem de SKU")
                .containsExactly("ORF-PAG-001", "ORF-PAG-002", "ORF-PAG-003");
    }

    // ------------------------------------------------------------------------------------------
    // EST-C013 — diagnóstico de divergência entre o contador de reservado e o ledger de reservas.
    //
    // Mesmo espírito do EST-C011 acima: é o único teste que exercita a query nativa de
    // findReservationMismatches; service e controller só delegam.
    // ------------------------------------------------------------------------------------------

    /** Varre todas as páginas e devolve só as linhas cujo SKU começa com o prefixo do teste. */
    private List<ReservationIntegrityMismatch> reservationMismatchesWithPrefix(String prefix) {
        PageResult<ReservationIntegrityMismatch> first = stockIntegrityRepository.findReservationMismatches(0, 100);
        List<ReservationIntegrityMismatch> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) {
            all.addAll(stockIntegrityRepository.findReservationMismatches(page, 100).content());
        }
        return all.stream().filter(m -> m.sku().startsWith(prefix)).toList();
    }

    @Test
    void reservationMismatch_detectaContadorAcimaDoLedger() {
        Warehouse warehouse = givenWarehouse("WH-RSV-CTR");
        stockBalanceRepository.save(StockBalance.of(null, "RSV-CTR-001", warehouse.id(),
                new BigDecimal("10.000"), new BigDecimal("5.000"), 0L));
        stockReservationRepository.save(StockReservation.create("RSV-CTR-001", warehouse.id(),
                new BigDecimal("3.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600), "gerente"));
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-CTR-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.reservedQuantity()).isEqualByComparingTo("5.000");
            assertThat(mismatch.activeReservationsTotal()).isEqualByComparingTo("3.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("2.000");
        });
    }

    @Test
    void reservationMismatch_detectaLedgerAcimaDoContador() {
        Warehouse warehouse = givenWarehouse("WH-RSV-LDG");
        // Nenhum stock_balance salvo de propósito: a divergência pode vir só do lado do ledger.
        stockReservationRepository.save(StockReservation.create("RSV-LDG-001", warehouse.id(),
                new BigDecimal("2.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600), "gerente"));
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-LDG-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.reservedQuantity()).isEqualByComparingTo("0");
            assertThat(mismatch.activeReservationsTotal()).isEqualByComparingTo("2.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("-2.000");
        });
    }

    @Test
    void reservationMismatch_naoAcusaQuandoContadorBateComLedger() {
        Warehouse warehouse = givenWarehouse("WH-RSV-OK");
        stockBalanceRepository.save(StockBalance.of(null, "RSV-OK-001", warehouse.id(),
                new BigDecimal("10.000"), new BigDecimal("4.000"), 0L));
        stockReservationRepository.save(StockReservation.create("RSV-OK-001", warehouse.id(),
                new BigDecimal("4.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600), "gerente"));
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-OK-")).isEmpty();
    }

    @Test
    void reservationMismatch_ignoraReservaJaResolvida() {
        Warehouse warehouse = givenWarehouse("WH-RSV-RES");
        stockBalanceRepository.save(StockBalance.of(null, "RSV-RES-001", warehouse.id(),
                new BigDecimal("10.000"), BigDecimal.ZERO, 0L));
        StockReservation resolvida = stockReservationRepository.save(StockReservation.create("RSV-RES-001",
                warehouse.id(), new BigDecimal("6.000"), "CHECKOUT:teste", Instant.now().plusSeconds(3600),
                "gerente"));
        stockReservationRepository.save(resolvida.released());
        flushAndClear();

        assertThat(reservationMismatchesWithPrefix("RSV-RES-"))
                .as("reserva RELEASED não conta no ledger ativo, e o contador já foi devolvido a zero")
                .isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // EST-F008 — diagnóstico de divergência entre o agregado (stock_balance) e a soma dos lotes
    // (stock_lot). Mesmo espírito do EST-C011/C013 acima: é o único teste que exercita a query
    // nativa de findLotMismatches; service e controller só delegam.
    // ------------------------------------------------------------------------------------------

    /** Varre todas as páginas e devolve só as linhas cujo SKU começa com o prefixo do teste. */
    private List<LotIntegrityMismatch> lotMismatchesWithPrefix(String prefix) {
        PageResult<LotIntegrityMismatch> first = stockIntegrityRepository.findLotMismatches(0, 100);
        List<LotIntegrityMismatch> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) {
            all.addAll(stockIntegrityRepository.findLotMismatches(page, 100).content());
        }
        return all.stream().filter(m -> m.sku().startsWith(prefix)).toList();
    }

    @Test
    void lotMismatch_detectaAgregadoAcimaDosLotes() {
        Warehouse warehouse = givenWarehouse("WH-LOT-AGG");
        productRepository.save(Product.create("LOT-AGG-001", "Essência", "essencia", List.of())
                .withLotTracked(true));
        stockBalanceRepository.save(StockBalance.of(null, "LOT-AGG-001", warehouse.id(),
                new BigDecimal("10.000"), 0L));
        stockLotRepository.save(StockLot.create("LOT-AGG-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(new BigDecimal("6.000")));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-AGG-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.balanceQuantity()).isEqualByComparingTo("10.000");
            assertThat(mismatch.lotsTotal()).isEqualByComparingTo("6.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("4.000");
        });
    }

    @Test
    void lotMismatch_detectaLotesAcimaDoAgregado() {
        Warehouse warehouse = givenWarehouse("WH-LOT-LTS");
        // Nenhum stock_balance salvo de propósito: a divergência pode vir só do lado dos lotes.
        stockLotRepository.save(StockLot.create("LOT-LTS-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(new BigDecimal("5.000")));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-LTS-")).singleElement().satisfies(mismatch -> {
            assertThat(mismatch.balanceQuantity()).isEqualByComparingTo("0");
            assertThat(mismatch.lotsTotal()).isEqualByComparingTo("5.000");
            assertThat(mismatch.difference()).isEqualByComparingTo("-5.000");
        });
    }

    @Test
    void lotMismatch_naoAcusaQuandoAgregadoBateComLotes() {
        Warehouse warehouse = givenWarehouse("WH-LOT-OK");
        productRepository.save(Product.create("LOT-OK-001", "Essência", "essencia", List.of())
                .withLotTracked(true));
        stockBalanceRepository.save(StockBalance.of(null, "LOT-OK-001", warehouse.id(),
                new BigDecimal("6.000"), 0L));
        stockLotRepository.save(StockLot.create("LOT-OK-001", warehouse.id(), "LOTE-A", LocalDate.of(2027, 1, 1))
                .receive(new BigDecimal("6.000")));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-OK-")).isEmpty();
    }

    /**
     * O filtro por {@code product.lot_tracked} existe justamente para isto: sem ele, todo SKU não
     * lote-rastreado com saldo (a maioria da base) apareceria como falso positivo, porque nunca
     * tem lote nenhum em {@code stock_lot}.
     */
    @Test
    void lotMismatch_ignoraProdutoNaoLoteRastreadoComSaldo() {
        Warehouse warehouse = givenWarehouse("WH-LOT-NT");
        productRepository.save(Product.create("LOT-NT-001", "Narguilé", "narguile", List.of()));
        stockBalanceRepository.save(StockBalance.of(null, "LOT-NT-001", warehouse.id(),
                new BigDecimal("10.000"), 0L));
        flushAndClear();

        assertThat(lotMismatchesWithPrefix("LOT-NT-")).isEmpty();
    }

    // ------------------------------------------------------------------------------------------
    // EST-F015 — receita de kit virtual. Nenhum teste aqui exercitava KitComponentRepositoryImpl
    // diretamente: o mapeamento domínio↔entidade e o delete-then-insert de replaceRecipe só
    // apareciam de lado, por ITs de controller.
    // ------------------------------------------------------------------------------------------

    @Test
    void kitComponent_findByKitSku_devolveComponentesMapeados() {
        kitComponentRepository.replaceRecipe("KIT-001", List.of(
                KitComponent.create("KIT-001", "KIT-001-COMP-A", new BigDecimal("2.000")),
                KitComponent.create("KIT-001", "KIT-001-COMP-B", new BigDecimal("3.500"))));
        flushAndClear();

        List<KitComponent> componentes = kitComponentRepository.findByKitSku("KIT-001");

        assertThat(componentes).hasSize(2).allSatisfy(c -> {
            assertThat(c.id()).isNotNull();
            assertThat(c.kitSku()).isEqualTo("KIT-001");
        });
        assertThat(componentes).extracting(KitComponent::componentSku)
                .containsExactlyInAnyOrder("KIT-001-COMP-A", "KIT-001-COMP-B");
        assertThat(componentes.stream()
                .filter(c -> c.componentSku().equals("KIT-001-COMP-A")).findFirst().orElseThrow().quantity())
                .isEqualByComparingTo("2.000");
        assertThat(componentes.stream()
                .filter(c -> c.componentSku().equals("KIT-001-COMP-B")).findFirst().orElseThrow().quantity())
                .isEqualByComparingTo("3.500");
    }

    /**
     * Prova que {@code replaceRecipe} é de fato delete-then-insert contra o banco (
     * {@code deleteByKitSku} + {@code flush()} + {@code saveAll}), e não deixa lixo dos
     * componentes antigos: a receita nova com só o componente C não pode trazer A nem B de volta.
     */
    @Test
    void kitComponent_replaceRecipe_substituiComponentesAntigosPorNovos() {
        kitComponentRepository.replaceRecipe("KIT-002", List.of(
                KitComponent.create("KIT-002", "KIT-002-COMP-A", BigDecimal.ONE),
                KitComponent.create("KIT-002", "KIT-002-COMP-B", BigDecimal.ONE)));
        flushAndClear();

        assertThat(kitComponentRepository.findByKitSku("KIT-002")).extracting(KitComponent::componentSku)
                .as("receita inicial gravada com A e B")
                .containsExactlyInAnyOrder("KIT-002-COMP-A", "KIT-002-COMP-B");

        kitComponentRepository.replaceRecipe("KIT-002",
                List.of(KitComponent.create("KIT-002", "KIT-002-COMP-C", BigDecimal.ONE)));
        flushAndClear();

        assertThat(kitComponentRepository.findByKitSku("KIT-002")).extracting(KitComponent::componentSku)
                .as("A e B somem, só C fica: sem isso a receita antiga vazaria pro consumo de estoque")
                .containsExactly("KIT-002-COMP-C");
    }

    @Test
    void kitComponent_isUsedAsComponent_trueSoParaSkuQueEComponenteDeAlgumKit() {
        kitComponentRepository.replaceRecipe("KIT-003",
                List.of(KitComponent.create("KIT-003", "KIT-003-COMP-USADO", BigDecimal.ONE)));
        flushAndClear();

        assertThat(kitComponentRepository.isUsedAsComponent("KIT-003-COMP-USADO"))
                .as("SKU é componente de KIT-003: não pode ser promovido a kit também").isTrue();
        assertThat(kitComponentRepository.isUsedAsComponent("KIT-003-COMP-NAO-USADO"))
                .as("SKU não aparece em nenhuma receita").isFalse();
    }

    // ── Resumo do catálogo (EST-F022 — GET /estoque/summary) ─────────────────
    //
    // Banco compartilhado com o resto da suíte: as asserções comparam antes/depois em vez de
    // total absoluto, para não quebrar por dado que já existia na base de dev.

    @Test
    void countProducts_eCountVariants_contamTudoIncluindoInativos() {
        long produtosAntes = productRepository.countProducts();
        long variantesAntes = productRepository.countVariants();

        Product inativo = productRepository.save(Product.create("SUM-CNT-001", "Produto", "sum-cnt",
                List.of(ProductVariant.create("SUM-CNT-001-A", List.of()),
                        ProductVariant.create("SUM-CNT-001-B", List.of()))));
        productRepository.save(inativo.withActive(false));
        flushAndClear();

        assertThat(productRepository.countProducts()).isEqualTo(produtosAntes + 1);
        assertThat(productRepository.countVariants()).isEqualTo(variantesAntes + 2);
    }

    @Test
    void findCategoryWithMostProducts_contaCorretamenteOsProdutosVinculados() {
        Category categoria = categoryRepository.save(Category.create("Cat Resumo " + System.nanoTime()));
        productRepository.save(Product.create("SUM-CAT-001", "P1", null, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(),
                categoria.id(), null));
        productRepository.save(Product.create("SUM-CAT-002", "P2", null, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), List.of(),
                categoria.id(), null));
        flushAndClear();

        // Não afirma que ESTA categoria vence globalmente (o banco é compartilhado e outra
        // categoria pode ter mais vínculos) — só que a vencedora tem pelo menos os 2 vínculos
        // criados agora, provando que o JOIN/GROUP BY conta certo.
        Optional<CategoryProductCount> found = productRepository.findCategoryWithMostProducts();
        assertThat(found).isPresent();
        assertThat(found.get().count()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void sumInventoryValueAtCost_somaSaldoXCustoEfetivoComHerancaDeVariacao() {
        BigDecimal antes = stockBalanceRepository.sumInventoryValueAtCost();
        Warehouse deposito = givenWarehouse("SUM-VAL-WH-" + System.nanoTime());

        // Produto simples: 10 un × custo 5.00 = 50.00.
        productRepository.save(Product.create("SUM-VAL-001", "Produto", "sum-val", List.of(),
                Pricing.of(new BigDecimal("5.00"), null, new BigDecimal("9.90"))));
        stockBalanceRepository.save(
                StockBalance.of(null, "SUM-VAL-001", deposito.id(), new BigDecimal("10"), 0L));

        // Variação com custo próprio: 4 un × custo 8.00 (não o do pai, 3.00) = 32.00.
        productRepository.save(Product.create("SUM-VAL-002", "Essência", "sum-val",
                List.of(ProductVariant.create("SUM-VAL-002-A", List.of(),
                        Pricing.of(new BigDecimal("8.00"), null, null))),
                Pricing.of(new BigDecimal("3.00"), null, new BigDecimal("15.00"))));
        stockBalanceRepository.save(
                StockBalance.of(null, "SUM-VAL-002-A", deposito.id(), new BigDecimal("4"), 0L));

        // Saldo sem custo conhecido: contribui zero, não invalida a soma.
        productRepository.save(Product.create("SUM-VAL-003", "Sem custo", "sum-val", List.of()));
        stockBalanceRepository.save(
                StockBalance.of(null, "SUM-VAL-003", deposito.id(), new BigDecimal("100"), 0L));
        flushAndClear();

        BigDecimal depois = stockBalanceRepository.sumInventoryValueAtCost();
        assertThat(depois.subtract(antes)).isEqualByComparingTo("82.00");
    }

    @Test
    void sumInventoryValueAtCost_usaAverageCostComFallbackParaCostPrice() {
        // EST-F007: average_cost tem precedência sobre costPrice manual quando os dois existem.
        BigDecimal antes = stockBalanceRepository.sumInventoryValueAtCost();
        Warehouse deposito = givenWarehouse("SUM-AVG-WH-" + System.nanoTime());

        // SKU com averageCost E costPrice manual: usa o averageCost (6.00), não o costPrice (5.00).
        // 10 un × 6.00 = 60.00.
        productRepository.save(Product.create("SUM-AVG-001", "Produto", "sum-avg", List.of(),
                Pricing.of(new BigDecimal("5.00"), null, new BigDecimal("9.90"))));
        stockBalanceRepository.save(
                StockBalance.of(null, "SUM-AVG-001", deposito.id(), new BigDecimal("10"), BigDecimal.ZERO,
                        new BigDecimal("6.00"), 0L));

        // SKU só com costPrice manual (nunca recebeu entrada com custo): fallback, como antes.
        // 4 un × 3.00 = 12.00.
        productRepository.save(Product.create("SUM-AVG-002", "Produto sem average", "sum-avg", List.of(),
                Pricing.of(new BigDecimal("3.00"), null, null)));
        stockBalanceRepository.save(
                StockBalance.of(null, "SUM-AVG-002", deposito.id(), new BigDecimal("4"), 0L));
        flushAndClear();

        BigDecimal depois = stockBalanceRepository.sumInventoryValueAtCost();
        assertThat(depois.subtract(antes)).isEqualByComparingTo("72.00");
    }

    @Test
    void stockBalance_roundTrip_preservaAverageCost() {
        Warehouse deposito = givenWarehouse("RT-AVG-WH-" + System.nanoTime());
        productRepository.save(Product.create("RT-AVG-001", "Produto", "rt-avg", List.of()));

        stockBalanceRepository.save(
                StockBalance.of(null, "RT-AVG-001", deposito.id(), new BigDecimal("20"), BigDecimal.ZERO,
                        new BigDecimal("6.00"), 0L));
        flushAndClear();

        Optional<StockBalance> found = stockBalanceRepository.findBySkuAndWarehouseId("RT-AVG-001", deposito.id());

        assertThat(found).isPresent();
        assertThat(found.get().averageCost()).isEqualByComparingTo("6.00");
    }

    @Test
    void stockBalance_roundTrip_averageCostNuloPermanceNulo() {
        Warehouse deposito = givenWarehouse("RT-AVG-NULL-WH-" + System.nanoTime());
        productRepository.save(Product.create("RT-AVG-002", "Produto", "rt-avg", List.of()));

        stockBalanceRepository.save(StockBalance.of(null, "RT-AVG-002", deposito.id(), new BigDecimal("5"), 0L));
        flushAndClear();

        Optional<StockBalance> found = stockBalanceRepository.findBySkuAndWarehouseId("RT-AVG-002", deposito.id());

        assertThat(found).isPresent();
        assertThat(found.get().averageCost()).isNull();
    }

    @Test
    void stockMovement_roundTrip_preservaUnitCost() {
        Warehouse deposito = givenWarehouse("RT-UC-WH-" + System.nanoTime());

        stockMovementRepository.save(StockMovement.create("RT-UC-001", deposito.id(), MovementType.ENTRADA,
                new BigDecimal("10"), "Recebimento", "gerente", null, new BigDecimal("7.50")));
        flushAndClear();

        PageResult<StockMovement> result = stockMovementRepository
                .findBySkuAndWarehouseId("RT-UC-001", deposito.id(), 0, 10);

        assertThat(result.content()).singleElement()
                .satisfies(m -> assertThat(m.unitCost()).isEqualByComparingTo("7.50"));
    }

    @Test
    void countAlerts_classificaCriticoEAtencaoPelaMesmaRegraDeSeverityFor() {
        ReorderAlertCounts antes = reorderPointRepository.countAlerts();
        Warehouse deposito = givenWarehouse("SUM-ALERT-WH-" + System.nanoTime());

        // Crítico: saldo zerado.
        reorderPointRepository.save(ReorderPoint.create("SUM-ALERT-CRIT", deposito.id(), new BigDecimal("10")));
        stockBalanceRepository.save(StockBalance.of(null, "SUM-ALERT-CRIT", deposito.id(), BigDecimal.ZERO, 0L));

        // Atenção: abaixo do mínimo (10), acima do limiar crítico (5).
        reorderPointRepository.save(ReorderPoint.create("SUM-ALERT-ATENCAO", deposito.id(), new BigDecimal("10")));
        stockBalanceRepository.save(
                StockBalance.of(null, "SUM-ALERT-ATENCAO", deposito.id(), new BigDecimal("7"), 0L));

        // OK: no mínimo.
        reorderPointRepository.save(ReorderPoint.create("SUM-ALERT-OK", deposito.id(), new BigDecimal("10")));
        stockBalanceRepository.save(StockBalance.of(null, "SUM-ALERT-OK", deposito.id(), new BigDecimal("10"), 0L));
        flushAndClear();

        ReorderAlertCounts depois = reorderPointRepository.countAlerts();
        assertThat(depois.criticos()).isEqualTo(antes.criticos() + 1);
        assertThat(depois.atencao()).isEqualTo(antes.atencao() + 1);
    }

    /** EST-F053 — a base de produto com variações não tem saldo: o mínimo nela não é alerta. */
    @Test
    void countAlerts_ignoraProdutoBaseComVariacoes() {
        Warehouse deposito = givenWarehouse("SUM-ALERT-PAI-" + System.nanoTime());
        productRepository.save(Product.create("SUM-ALERT-PAI", "Rosh", "acessorio",
                List.of(ProductVariant.create("SUM-ALERT-PAI-AZ", List.of(new ProductAttribute("Cor", "Azul"))))));
        reorderPointRepository.save(ReorderPoint.create("SUM-ALERT-PAI", deposito.id(), new BigDecimal("3")));
        flushAndClear();
        ReorderAlertCounts semAVariacao = reorderPointRepository.countAlerts();

        // A variação, com mínimo e sem saldo, conta como crítico normalmente.
        reorderPointRepository.save(ReorderPoint.create("SUM-ALERT-PAI-AZ", deposito.id(), new BigDecimal("3")));
        flushAndClear();

        assertThat(reorderPointRepository.countAlerts().criticos()).isEqualTo(semAVariacao.criticos() + 1);
    }

    // ── Marcas do catálogo (item 3) ───────────────────────────────────────────

    @Autowired BrandRepositoryImpl brandRepository;

    @Test
    void brand_roundTrip() {
        Brand saved = brandRepository.save(Brand.create("BrandRT Zomo"));
        flushAndClear();

        Brand reloaded = brandRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.name()).isEqualTo("BrandRT Zomo");
        assertThat(reloaded.active()).isTrue();
    }

    @Test
    void brand_findByName_ignoraCaixa() {
        brandRepository.save(Brand.create("BrandCase Alfaraby"));
        flushAndClear();

        assertThat(brandRepository.findByName("brandcase alfaraby")).isPresent();
        assertThat(brandRepository.findByName("BRANDCASE ALFARABY")).isPresent();
    }

    @Test
    void renameBrand_atualizaAColunaDenormalizadaDosProdutosVinculados() {
        Brand marca = brandRepository.save(Brand.create("BrandRen Antiga"));
        productRepository.save(Product.of(null, "BRANDREN-001", "Produto 1", "cat", true, List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, "BrandRen Antiga", null, false, false, null, null,
                List.of(), List.of(), null, null, null, false, false, true, true, ProductStatus.ATIVO,
                marca.id()));
        flushAndClear();

        int atualizados = productRepository.renameBrand(marca.id(), "BrandRen Nova");
        flushAndClear();

        assertThat(atualizados).isEqualTo(1);
        assertThat(productRepository.findBySku("BRANDREN-001").orElseThrow().brand()).isEqualTo("BrandRen Nova");
    }

    @Test
    void product_roundTripDoVinculoDeMarca() {
        Brand marca = brandRepository.save(Brand.create("BrandVinc Zomo"));
        productRepository.save(Product.of(null, "BRANDVINC-001", "Produto", "cat", true, List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, "BrandVinc Zomo", null, false, false, null, null, List.of(), List.of(),
                null, null, null, false, false, true, true, ProductStatus.ATIVO, marca.id()));
        flushAndClear();

        Product reloaded = productRepository.findBySku("BRANDVINC-001").orElseThrow();

        assertThat(reloaded.brandId()).isEqualTo(marca.id());
        assertThat(reloaded.brand()).isEqualTo("BrandVinc Zomo");
    }

    // ── Vocabulário de atributos (item 5) ─────────────────────────────────────

    @Autowired AttributeTypeRepositoryImpl attributeTypeRepository;

    @Test
    void attributeType_roundTrip() {
        AttributeType saved = attributeTypeRepository.save(AttributeType.create("AttrRT Intensidade"));
        flushAndClear();

        List<AttributeType> all = attributeTypeRepository.findAllOrderByName();

        assertThat(all).extracting(AttributeType::id).contains(saved.id());
    }

    @Test
    void attributeType_findByName_ignoraCaixa() {
        attributeTypeRepository.save(AttributeType.create("AttrCase Potência"));
        flushAndClear();

        assertThat(attributeTypeRepository.findByName("attrcase potência")).isPresent();
    }

    // ── Histórico de compras — vínculo StockMovement→GoodsReceipt (item 2) ───

    @Test
    void stockMovement_goodsReceiptId_roundTrip() {
        Warehouse deposito = givenWarehouse("PURCH-WH-" + System.nanoTime());
        productRepository.save(Product.create("PURCH-001", "Essência", "essencias", List.of()));
        stockMovementRepository.save(StockMovement.create("PURCH-001", deposito.id(), MovementType.ENTRADA,
                new BigDecimal("24"), "Recebimento de mercadoria - fornecedor #3", "gerente", null,
                new BigDecimal("42.50"), 87L));
        flushAndClear();

        PageResult<StockMovement> result = stockMovementRepository
                .findEntradasBySkuAndWarehouseId("PURCH-001", deposito.id(), 0, 20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).goodsReceiptId()).isEqualTo(87L);
        assertThat(result.content().get(0).unitCost()).isEqualByComparingTo("42.50");
    }

    @Test
    void stockMovement_semGoodsReceiptId_ficaNulo() {
        // Movimentação manual (registerMovement), sem vínculo de recebimento — limitação conhecida
        // de dado histórico e do caminho manual, não erro.
        Warehouse deposito = givenWarehouse("PURCH-WH2-" + System.nanoTime());
        productRepository.save(Product.create("PURCH-002", "Essência", "essencias", List.of()));
        stockMovementRepository.save(StockMovement.create("PURCH-002", deposito.id(), MovementType.ENTRADA,
                new BigDecimal("10"), "Ajuste manual", "gerente"));
        flushAndClear();

        PageResult<StockMovement> result = stockMovementRepository
                .findEntradasBySkuAndWarehouseId("PURCH-002", deposito.id(), 0, 20);

        assertThat(result.content().get(0).goodsReceiptId()).isNull();
    }

    @Test
    void listMovements_filtraPorTipoEIntervaloDeData() {
        // item 6 — o paginador e a tabela precisam bater: filtro no banco, não no cliente.
        Warehouse deposito = givenWarehouse("MOVFILT-WH-" + System.nanoTime());
        productRepository.save(Product.create("MOVFILT-001", "Produto", "cat", List.of()));
        stockMovementRepository.save(StockMovement.create("MOVFILT-001", deposito.id(), MovementType.ENTRADA,
                new BigDecimal("10"), "Entrada", "gerente"));
        stockMovementRepository.save(StockMovement.create("MOVFILT-001", deposito.id(), MovementType.SAIDA,
                new BigDecimal("3"), "Saída", "gerente"));
        flushAndClear();

        PageResult<StockMovement> apenasEntradas = stockMovementRepository.findBySkuAndWarehouseId("MOVFILT-001",
                deposito.id(), MovementType.ENTRADA, null, null, 0, 20);
        PageResult<StockMovement> forDoIntervalo = stockMovementRepository.findBySkuAndWarehouseId("MOVFILT-001",
                deposito.id(), null, Instant.now().plusSeconds(3600), null, 0, 20);

        assertThat(apenasEntradas.content()).extracting(StockMovement::type).containsExactly(MovementType.ENTRADA);
        assertThat(forDoIntervalo.content()).isEmpty();
    }

    // ── DELETE reorder-point (item 4) ─────────────────────────────────────────

    @Test
    void deleteReorderPoint_removeQuandoExiste() {
        Warehouse deposito = givenWarehouse("DELRP-WH-" + System.nanoTime());
        reorderPointRepository.save(ReorderPoint.create("DELRP-001", deposito.id(), new BigDecimal("10")));
        flushAndClear();

        reorderPointRepository.deleteBySkuAndWarehouseId("DELRP-001", deposito.id());
        flushAndClear();

        assertThat(reorderPointRepository.findBySkuAndWarehouseId("DELRP-001", deposito.id())).isEmpty();
    }

    @Test
    void deleteReorderPoint_idempotenteQuandoNaoExiste() {
        Warehouse deposito = givenWarehouse("DELRP-WH2-" + System.nanoTime());

        // Não lança e não afeta nenhuma outra linha — mesma régua do endpoint (204 sempre).
        reorderPointRepository.deleteBySkuAndWarehouseId("SEM-MINIMO", deposito.id());
    }

    // ── existsBySku para bloquear DELETE de variante (item 8) ─────────────────

    @Test
    void stockBalance_existsBySku_refleteSaldoGravado() {
        Warehouse deposito = givenWarehouse("EXVAR-WH-" + System.nanoTime());
        String sku = "EXVAR-BAL-" + System.nanoTime();

        assertThat(stockBalanceRepository.existsBySku(sku)).isFalse();

        stockBalanceRepository.save(StockBalance.of(null, sku, deposito.id(), new BigDecimal("5"), 0L));
        flushAndClear();

        assertThat(stockBalanceRepository.existsBySku(sku)).isTrue();
    }

    @Test
    void stockMovement_existsBySku_refleteMovimentacaoGravada() {
        Warehouse deposito = givenWarehouse("EXVAR-WH2-" + System.nanoTime());
        String sku = "EXVAR-MOV-" + System.nanoTime();

        assertThat(stockMovementRepository.existsBySku(sku)).isFalse();

        stockMovementRepository.save(StockMovement.create(sku, deposito.id(), MovementType.ENTRADA,
                new BigDecimal("5"), "Entrada", "gerente"));
        flushAndClear();

        assertThat(stockMovementRepository.existsBySku(sku)).isTrue();
    }

    // ── Lista de Reposição (item 1) ────────────────────────────────────────────

    @Autowired ReplenishmentListRepositoryImpl replenishmentListRepository;

    private ReplenishmentListItem sampleReplenishmentItem(String sku, Long warehouseId) {
        return ReplenishmentListItem.create(sku, warehouseId, "Essência Babylon 50g", "Essências", "Zomo",
                MeasurementUnit.UN, new BigDecimal("2.000"), new BigDecimal("10.000"), new BigDecimal("8.000"),
                new BigDecimal("12"), new BigDecimal("45.00"), new BigDecimal("24.000"), new BigDecimal("42.50"),
                Instant.parse("2026-07-01T10:00:00Z"), "pedir junto com o pedido da Zomo", "jeff");
    }

    @Test
    void replenishmentListItem_roundTrip() {
        Warehouse deposito = givenWarehouse("REPL-WH-" + System.nanoTime());
        String sku = "REPL-001-" + System.nanoTime();

        replenishmentListRepository.save(sampleReplenishmentItem(sku, deposito.id()));
        flushAndClear();

        ReplenishmentListItem reloaded = replenishmentListRepository
                .findBySkuAndWarehouseId(sku, deposito.id()).orElseThrow();

        assertThat(reloaded.productNameSnapshot()).isEqualTo("Essência Babylon 50g");
        assertThat(reloaded.unitSnapshot()).isEqualTo(MeasurementUnit.UN);
        assertThat(reloaded.currentStockSnapshot()).isEqualByComparingTo("2.000");
        assertThat(reloaded.minStockSnapshot()).isEqualByComparingTo("10.000");
        assertThat(reloaded.suggestedQuantitySnapshot()).isEqualByComparingTo("8.000");
        assertThat(reloaded.quantity()).isEqualByComparingTo("12");
        assertThat(reloaded.previousPurchaseQuantitySnapshot()).isEqualByComparingTo("24.000");
        assertThat(reloaded.note()).isEqualTo("pedir junto com o pedido da Zomo");
        assertThat(reloaded.createdBy()).isEqualTo("jeff");
    }

    @Test
    void replenishmentListItem_upsertPorSkuEDeposito_substituiNaoDuplica() {
        Warehouse deposito = givenWarehouse("REPL-WH2-" + System.nanoTime());
        String sku = "REPL-002-" + System.nanoTime();

        replenishmentListRepository.save(sampleReplenishmentItem(sku, deposito.id()));
        flushAndClear();
        replenishmentListRepository.save(sampleReplenishmentItem(sku, deposito.id())
                .withQuantityAndNote(new BigDecimal("99"), "atualizado"));
        flushAndClear();

        List<ReplenishmentListItem> items = replenishmentListRepository.findByWarehouseId(deposito.id()).stream()
                .filter(i -> i.sku().equals(sku))
                .toList();

        assertThat(items).hasSize(1);
        assertThat(items.get(0).quantity()).isEqualByComparingTo("99");
        assertThat(items.get(0).note()).isEqualTo("atualizado");
    }

    @Test
    void replenishmentListItem_deleteBySkuAndWarehouseId() {
        Warehouse deposito = givenWarehouse("REPL-WH3-" + System.nanoTime());
        String sku = "REPL-003-" + System.nanoTime();
        replenishmentListRepository.save(sampleReplenishmentItem(sku, deposito.id()));
        flushAndClear();

        replenishmentListRepository.deleteBySkuAndWarehouseId(sku, deposito.id());
        flushAndClear();

        assertThat(replenishmentListRepository.findBySkuAndWarehouseId(sku, deposito.id())).isEmpty();
    }

    @Test
    void replenishmentListItem_deleteByWarehouseId_limpaAListaInteira() {
        Warehouse deposito = givenWarehouse("REPL-WH4-" + System.nanoTime());
        replenishmentListRepository.save(sampleReplenishmentItem("REPL-004A-" + System.nanoTime(), deposito.id()));
        replenishmentListRepository.save(sampleReplenishmentItem("REPL-004B-" + System.nanoTime(), deposito.id()));
        flushAndClear();

        replenishmentListRepository.deleteByWarehouseId(deposito.id());
        flushAndClear();

        assertThat(replenishmentListRepository.findByWarehouseId(deposito.id())).isEmpty();
    }
}
