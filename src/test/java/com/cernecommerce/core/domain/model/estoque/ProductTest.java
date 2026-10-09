package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ProductTest {

    private Product product() {
        return Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true,
                List.of(ProductVariant.of(2L, "NARG-001-M", List.of(new ProductAttribute("sabor", "menta")), true)));
    }

    @Test
    void create_buildsActiveProductWithoutId() {
        Product created = Product.create("NARG-001", "Narguile Aladin", "narguile", List.of());

        assertThat(created.id()).isNull();
        assertThat(created.active()).isTrue();
        assertThat(created.variants()).isEmpty();
    }

    @Test
    void nullVariantsBecomesEmptyList() {
        assertThat(Product.create("NARG-001", "Narguile", "cat", null).variants()).isEmpty();
    }

    @Test
    void variantsAreDefensivelyCopied() {
        List<ProductVariant> mutable = new ArrayList<>();
        mutable.add(ProductVariant.of(2L, "NARG-001-M", List.of(), true));

        Product created = Product.create("NARG-001", "Narguile", "cat", mutable);
        mutable.clear();

        assertThat(created.variants()).hasSize(1);
    }

    @Test
    void throwsWhenSkuIsBlank() {
        assertThatThrownBy(() -> Product.create("  ", "Narguile", "cat", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenNameIsBlank() {
        assertThatThrownBy(() -> Product.create("NARG-001", "", "cat", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // EST-F018 — alteração parcial e desativação

    @Test
    void withDetails_nullMantemOCampo() {
        Product original = product();

        Product soNome = original.withDetails("Narguilé Aladin 2.0", null);
        assertThat(soNome.name()).isEqualTo("Narguilé Aladin 2.0");
        assertThat(soNome.category()).isEqualTo("narguile");

        Product soCategoria = original.withDetails(null, "narguile-premium");
        assertThat(soCategoria.name()).isEqualTo("Narguile Aladin");
        assertThat(soCategoria.category()).isEqualTo("narguile-premium");
    }

    @Test
    void withDetails_preservaIdSkuActiveEVariacoes() {
        Product original = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", false,
                List.of(ProductVariant.of(2L, "NARG-001-M", List.of(), true)));

        Product updated = original.withDetails("Outro nome", "outra-categoria");

        assertThat(updated.id()).isEqualTo(1L);
        assertThat(updated.sku()).as("sku é referenciado como texto livre pelas tabelas de estoque").isEqualTo("NARG-001");
        assertThat(updated.active()).as("ativação tem endpoint próprio").isFalse();
        assertThat(updated.variants()).extracting(ProductVariant::sku).containsExactly("NARG-001-M");
    }

    /**
     * Limitação conhecida da semântica "null = manter": não há como limpar a categoria por este
     * caminho, só trocá-la.
     */
    @Test
    void withDetails_naoConsegueLimparACategoria() {
        assertThat(product().withDetails(null, null).category()).isEqualTo("narguile");
    }

    @Test
    void withDetails_naoDeixaBurlarOsInvariantes() {
        assertThatThrownBy(() -> product().withDetails("   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withActive_alternaPreservandoORestante() {
        Product desativado = product().withActive(false);

        assertThat(desativado.active()).isFalse();
        assertThat(desativado.id()).isEqualTo(1L);
        assertThat(desativado.sku()).isEqualTo("NARG-001");
        assertThat(desativado.name()).isEqualTo("Narguile Aladin");
        assertThat(desativado.category()).isEqualTo("narguile");
        assertThat(desativado.variants()).hasSize(1);
        assertThat(desativado.withActive(true).active()).isTrue();
    }

    // EST-F015 — kits virtuais

    @Test
    void type_defaultsToSimplesWhenOmitted() {
        assertThat(Product.create("NARG-001", "Narguile", "cat", List.of()).type())
                .isEqualTo(ProductType.SIMPLES);
        assertThat(product().type()).isEqualTo(ProductType.SIMPLES);
    }

    @Test
    void withType_promotesPreservingTheRest() {
        Product kit = product().withType(ProductType.KIT);

        assertThat(kit.type()).isEqualTo(ProductType.KIT);
        assertThat(kit.isKit()).isTrue();
        assertThat(kit.id()).isEqualTo(1L);
        assertThat(kit.sku()).isEqualTo("NARG-001");
        assertThat(kit.variants()).hasSize(1);
    }

    @Test
    void isKit_falseForSimples() {
        assertThat(product().isKit()).isFalse();
    }

    // EST-F023 — rascunho de produto/kit

    @Test
    void status_defaultsToAtivoWhenOmitted() {
        assertThat(Product.create("NARG-001", "Narguile", "cat", List.of()).status())
                .isEqualTo(ProductStatus.ATIVO);
        assertThat(product().status()).isEqualTo(ProductStatus.ATIVO);
    }

    @Test
    void withStatus_promotesPreservingTheRest() {
        Product rascunho = product().withStatus(ProductStatus.RASCUNHO);

        assertThat(rascunho.status()).isEqualTo(ProductStatus.RASCUNHO);
        assertThat(rascunho.isDraft()).isTrue();
        assertThat(rascunho.id()).isEqualTo(1L);
        assertThat(rascunho.sku()).isEqualTo("NARG-001");
        assertThat(rascunho.active()).as("status é ortogonal a active").isTrue();
        assertThat(rascunho.withStatus(ProductStatus.ATIVO).status()).isEqualTo(ProductStatus.ATIVO);
    }

    @Test
    void isDraft_falseForAtivo() {
        assertThat(product().isDraft()).isFalse();
    }

    // EST-F008 — lote e validade

    @Test
    void lotTracked_defaultsToFalseWhenOmitted() {
        assertThat(Product.create("NARG-001", "Narguile", "cat", List.of()).lotTracked()).isFalse();
        assertThat(product().lotTracked()).isFalse();
    }

    @Test
    void withLotTracked_alternaPreservandoORestante() {
        Product rastreado = product().withLotTracked(true);

        assertThat(rastreado.lotTracked()).isTrue();
        assertThat(rastreado.id()).isEqualTo(1L);
        assertThat(rastreado.sku()).isEqualTo("NARG-001");
        assertThat(rastreado.type()).isEqualTo(ProductType.SIMPLES);
        assertThat(rastreado.withLotTracked(false).lotTracked()).isFalse();
    }

    @Test
    void kitLoteRastreado_lancaIllegalArgument() {
        assertThatThrownBy(() -> Product.create("KIT-001", "Kit", "cat", List.of(), null,
                ProductType.KIT, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kit não pode ser lote-rastreado");
    }

    @Test
    void withType_promovendoParaKitQuandoJaLoteRastreado_lancaIllegalArgument() {
        Product loteRastreado = product().withLotTracked(true);

        assertThatThrownBy(() -> loteRastreado.withType(ProductType.KIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kit não pode ser lote-rastreado");
    }

    // Campos de marketing: superPromo, description, videoUrl, images

    @Test
    void create_overloadAntigoDefaultaCamposDeMarketing() {
        Product created = Product.create("NARG-001", "Narguile Aladin", "narguile", List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, "Aladin", "http://img.png", true);

        assertThat(created.superPromo()).isFalse();
        assertThat(created.description()).isNull();
        assertThat(created.videoUrl()).isNull();
        assertThat(created.images()).isEmpty();
    }

    @Test
    void create_overloadCanonicoAceitaTodosOsCamposDeMarketing() {
        Product created = Product.create("NARG-001", "Narguile Aladin", "narguile", List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, "Aladin", "http://img.png", true, true,
                "Descrição longa", "http://video.mp4", List.of("http://img1.png", "http://img2.png"));

        assertThat(created.superPromo()).isTrue();
        assertThat(created.description()).isEqualTo("Descrição longa");
        assertThat(created.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(created.images()).containsExactly("http://img1.png", "http://img2.png");
    }

    @Test
    void of_overloadAntigoDefaultaCamposDeMarketing() {
        Product reconstituted = Product.of(1L, "NARG-001", "Narguile Aladin", "narguile", true, List.of(),
                Pricing.empty(), ProductType.SIMPLES, false, "Aladin", "http://img.png", true);

        assertThat(reconstituted.superPromo()).isFalse();
        assertThat(reconstituted.description()).isNull();
        assertThat(reconstituted.videoUrl()).isNull();
        assertThat(reconstituted.images()).isEmpty();
    }

    @Test
    void images_saoDefensivamenteCopiadas() {
        List<String> mutable = new ArrayList<>();
        mutable.add("http://img1.png");

        Product created = Product.create("NARG-001", "Narguile", "cat", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, mutable);
        mutable.clear();

        assertThat(created.images()).hasSize(1);
    }

    @Test
    void images_nuloViraListaVazia() {
        Product created = Product.create("NARG-001", "Narguile", "cat", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, null);

        assertThat(created.images()).isEmpty();
    }

    @Test
    void withImages_substituiAGaleriaInteiraPreservandoORestante() {
        Product original = product();

        Product comGaleria = original.withImages(List.of("http://img1.png", "http://img2.png"));

        assertThat(comGaleria.images()).containsExactly("http://img1.png", "http://img2.png");
        assertThat(comGaleria.id()).isEqualTo(1L);
        assertThat(comGaleria.sku()).isEqualTo("NARG-001");
    }

    @Test
    void withImages_listaVaziaLimpaAGaleria() {
        Product comGaleria = product().withImages(List.of("http://img1.png"));

        Product semGaleria = comGaleria.withImages(List.of());

        assertThat(semGaleria.images()).isEmpty();
    }

    @Test
    void withSuperPromo_alternaPreservandoORestante() {
        Product marcado = product().withSuperPromo(true);

        assertThat(marcado.superPromo()).isTrue();
        assertThat(marcado.id()).isEqualTo(1L);
        assertThat(marcado.sku()).isEqualTo("NARG-001");
        assertThat(marcado.withSuperPromo(false).superPromo()).isFalse();
    }

    @Test
    void withDetails_6ArgumentosAlteraDescricaoEVideoUrl() {
        Product original = product();

        Product atualizado = original.withDetails(null, null, null, null, "Nova descrição", "http://video.mp4");

        assertThat(atualizado.description()).isEqualTo("Nova descrição");
        assertThat(atualizado.videoUrl()).isEqualTo("http://video.mp4");
        assertThat(atualizado.name()).isEqualTo("Narguile Aladin");
    }

    @Test
    void withDetails_6Argumentos_nuloMantemDescricaoEVideoUrl() {
        Product comDetalhes = product().withDetails(null, null, null, null, "Descrição", "http://video.mp4");

        Product semMudanca = comDetalhes.withDetails(null, null, null, null, null, null);

        assertThat(semMudanca.description()).isEqualTo("Descrição");
        assertThat(semMudanca.videoUrl()).isEqualTo("http://video.mp4");
    }

    @Test
    void withDetails_4ArgumentosNaoTocaEmDescricaoNemVideoUrl() {
        Product comDetalhes = product().withDetails(null, null, null, null, "Descrição", "http://video.mp4");

        Product atualizado = comDetalhes.withDetails("Novo nome", null, null, null);

        assertThat(atualizado.name()).isEqualTo("Novo nome");
        assertThat(atualizado.description()).isEqualTo("Descrição");
        assertThat(atualizado.videoUrl()).isEqualTo("http://video.mp4");
    }

    @Test
    void withDetails_naoAlteraImages() {
        Product comGaleria = product().withImages(List.of("http://img1.png"));

        Product atualizado = comGaleria.withDetails("Novo nome", null, null, null, null, null);

        assertThat(atualizado.images()).containsExactly("http://img1.png");
    }

    // ── Atributos do próprio SKU pai ──────────────────────────────────────────

    @Test
    void sobrecargasAntigas_nascemSemAtributosDeRaiz() {
        // Retrocompatibilidade: quem já chamava create/of sem atributos continua válido.
        assertThat(Product.create("P-1", "Nome", "cat", List.of()).attributes()).isEmpty();
        assertThat(Product.of(1L, "P-1", "Nome", "cat", true, List.of()).attributes()).isEmpty();
    }

    @Test
    void atributosNulosViramListaVaziaENuncaNull() {
        Product product = Product.create("P-1", "Nome", "cat", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), null);
        assertThat(product.attributes()).isNotNull().isEmpty();
    }

    @Test
    void atributosSaoCopiadosDefensivamente() {
        List<ProductAttribute> mutavel = new ArrayList<>();
        mutavel.add(new ProductAttribute("Sabor", "Menta"));

        Product product = Product.create("P-1", "Nome", "cat", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(), mutavel);
        mutavel.add(new ProductAttribute("Cor", "Azul"));

        assertThat(product.attributes()).hasSize(1);
        assertThatThrownBy(() -> product.attributes().add(new ProductAttribute("X", "Y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withAttributes_substituiOConjuntoInteiroEPreservaOResto() {
        Product product = Product.create("P-1", "Nome", "cat",
                List.of(ProductVariant.create("P-1-A", List.of(new ProductAttribute("Sabor", "Menta")))),
                Pricing.empty(), ProductType.SIMPLES, false, "marca", "img", true, false, "desc", "video",
                List.of("g1.png"), List.of(new ProductAttribute("Origem", "Brasil")));

        Product updated = product.withAttributes(List.of(new ProductAttribute("Peso", "50g")));

        assertThat(updated.attributes())
                .extracting(ProductAttribute::type, ProductAttribute::value)
                .containsExactly(tuple("Peso", "50g"));
        // Nada mais pode ter mudado.
        assertThat(updated.name()).isEqualTo("Nome");
        assertThat(updated.brand()).isEqualTo("marca");
        assertThat(updated.images()).containsExactly("g1.png");
        assertThat(updated.variants()).hasSize(1);
    }

    @Test
    void atributosDeRaizNaoSeMisturamComOsDaVariacao() {
        // São coleções separadas de propósito: o da variação identifica, o da raiz só descreve.
        Product product = Product.create("P-1", "Nome", "cat",
                List.of(ProductVariant.create("P-1-A", List.of(new ProductAttribute("Sabor", "Menta")))),
                Pricing.empty(), ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil")));

        assertThat(product.attributes()).extracting(ProductAttribute::type).containsExactly("Origem");
        assertThat(product.variants().get(0).attributes()).extracting(ProductAttribute::type)
                .containsExactly("Sabor");
    }

    @Test
    void withAttributes_aceitaListaVaziaParaLimpar() {
        Product product = Product.create("P-1", "Nome", "cat", List.of(), Pricing.empty(),
                ProductType.SIMPLES, false, null, null, false, false, null, null, List.of(),
                List.of(new ProductAttribute("Origem", "Brasil")));

        assertThat(product.withAttributes(List.of()).attributes()).isEmpty();
    }

    // ── Precedência de preço variação → pai (EST-F020) ───────────────────────

    private static Product comGrade(Pricing precoDoPai, Pricing precoDaVariacaoA) {
        return Product.create("PAI-1", "Essência", "essencia",
                List.of(ProductVariant.create("PAI-1-A", List.of(), precoDaVariacaoA),
                        ProductVariant.create("PAI-1-B", List.of())),
                precoDoPai);
    }

    @Test
    void skuDoPai_resolveSempreParaOPrecoDoPai() {
        Product product = comGrade(Pricing.of(null, null, new BigDecimal("30.00")),
                Pricing.of(null, null, new BigDecimal("99.00")));

        assertThat(product.effectivePricingFor("PAI-1").salePrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void variacaoComPrecoProprio_sobrescreveODoPai() {
        Product product = comGrade(Pricing.of(null, null, new BigDecimal("30.00")),
                Pricing.of(null, null, new BigDecimal("99.00")));

        assertThat(product.effectivePricingFor("PAI-1-A").salePrice()).isEqualByComparingTo("99.00");
    }

    @Test
    void variacaoSemPrecoProprio_herdaODoPai() {
        // É o comportamento histórico e segue sendo o padrão.
        Product product = comGrade(Pricing.of(null, null, new BigDecimal("30.00")),
                Pricing.of(null, null, new BigDecimal("99.00")));

        assertThat(product.effectivePricingFor("PAI-1-B").salePrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void variacaoComPricingVazio_herdaEmVezDeApagarOPrecoDoPai() {
        Product product = comGrade(Pricing.of(null, null, new BigDecimal("30.00")), Pricing.empty());

        assertThat(product.effectivePricingFor("PAI-1-A").salePrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void skuDesconhecido_caiNoPrecoDoPai() {
        // Não lança: quem valida existência de SKU é o service, não a resolução de preço.
        Product product = comGrade(Pricing.of(null, null, new BigDecimal("30.00")), null);

        assertThat(product.effectivePricingFor("NAO-EXISTE").salePrice()).isEqualByComparingTo("30.00");
    }

    @Test
    void produtoSemGrade_resolveParaOProprioPreco() {
        Product product = Product.create("SIMPLES-1", "Carvão", "carvao", List.of(),
                Pricing.of(null, null, new BigDecimal("12.00")));

        assertThat(product.effectivePricingFor("SIMPLES-1").salePrice()).isEqualByComparingTo("12.00");
    }

    @Test
    void variacaoQueDeclaraSoOOriginalPrice_naoPerdeOPrecoDeVendaDoPai() {
        // Herança é POR CAMPO. Fosse tudo-ou-nada, esta variação ficaria sem preço de venda
        // nenhum — silenciosamente, porque o do pai teria sido descartado junto.
        Product product = Product.create("PAI-2", "Essência", "essencia",
                List.of(ProductVariant.create("PAI-2-A", List.of(),
                        Pricing.of(null, null, null, new BigDecimal("120.00")))),
                Pricing.of(null, null, new BigDecimal("30.00")));

        Pricing efetivo = product.effectivePricingFor("PAI-2-A");

        assertThat(efetivo.salePrice()).as("herdado do pai").isEqualByComparingTo("30.00");
        assertThat(efetivo.originalPrice()).as("próprio da variação").isEqualByComparingTo("120.00");
    }

    @Test
    void variacaoQueDeclaraSoOCusto_sobrescreveOCustoEHerdaOPrecoDeVenda() {
        Product product = Product.create("PAI-3", "Essência", "essencia",
                List.of(ProductVariant.create("PAI-3-A", List.of(),
                        Pricing.of(new BigDecimal("40.00"), null, null))),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("30.00")));

        Pricing efetivo = product.effectivePricingFor("PAI-3-A");

        assertThat(efetivo.costPrice()).isEqualByComparingTo("40.00");
        assertThat(efetivo.salePrice()).isEqualByComparingTo("30.00");
        // E a margem passa a refletir o custo real da variação, não o do pai.
        assertThat(efetivo.marginAmount()).isEqualByComparingTo("-10.00");
    }

    // ── Produto base com variações (EST-F036) ────────────────────────────────────────────────

    private static Product lmComCores() {
        return Product.create("LM", "LM", "Cigarros", List.of(
                ProductVariant.create("LM-AZUL", List.of(new ProductAttribute("cor", "azul"))),
                ProductVariant.create("LM-VERM", List.of(new ProductAttribute("cor", "vermelho")))));
    }

    /**
     * "LM" com azul e vermelho: a base não existe na prateleira — o estoque está nas cores —, e por
     * padrão não se vende. As variações, sim.
     */
    @Test
    void baseComVariacoes_naoEVendavelPorPadrao_masAsVariacoesSao() {
        Product lm = lmComCores();

        assertThat(lm.parentSellable()).isFalse();
        assertThat(lm.isSellable("LM")).isFalse();
        assertThat(lm.isSellable("LM-AZUL")).isTrue();
    }

    /** Sem variações, a base é o próprio item da prateleira: sempre vendável, o flag não se aplica. */
    @Test
    void produtoSemVariacoes_eSempreVendavel() {
        Product carvao = Product.create("CARVAO-1KG", "Carvão", "Carvão", List.of());

        assertThat(carvao.isSellable("CARVAO-1KG")).isTrue();
    }

    /** O dono pode ligar a venda da base, quando ela existe de verdade como item. */
    @Test
    void withParentSellable_liberaABase() {
        Product lm = lmComCores().withParentSellable(true);

        assertThat(lm.isSellable("LM")).isTrue();
        assertThat(lm.withActive(false).parentSellable()).as("sobrevive às cópias").isTrue();
    }
}
