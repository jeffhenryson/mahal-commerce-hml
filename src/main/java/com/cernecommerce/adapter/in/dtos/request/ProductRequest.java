package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.ProductStatus;
import com.cernecommerce.core.domain.model.estoque.ProductType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductRequest {
    @NotBlank
    @Size(min = 3, max = 50)
    private String sku;

    @NotBlank
    @Size(max = 255)
    private String name;

    /**
     * Nome da categoria, como texto livre. Continua sendo o caminho aceito e é o que o admin usa
     * hoje: um nome desconhecido <b>cria</b> a categoria correspondente. Quem já souber o id
     * prefira {@code categoryId}, que dispensa a busca por nome.
     */
    @Size(max = 100)
    private String category;

    /**
     * Vínculo direto com uma categoria existente. Quando informado, vence sobre {@code category}
     * e o nome é resolvido a partir dele. Id inexistente é 404.
     */
    private Long categoryId;

    /**
     * Nome da marca, como texto livre. Continua sendo o caminho aceito: um nome desconhecido
     * <b>cria</b> a marca correspondente. Quem já souber o id prefira {@code brandId}, que
     * dispensa a busca por nome.
     */
    @Size(max = 100)
    private String brand;

    /**
     * Vínculo direto com uma marca existente. Quando informado, vence sobre {@code brand} e o
     * nome é resolvido a partir dele. Id inexistente é 404.
     */
    private Long brandId;

    /** Estágio 01 do admin — link de imagem cadastrado manualmente, não upload de arquivo. */
    @Size(max = 2048)
    private String imageUrl;

    /** Estágio 01 do admin — produto em promoção. Omitido, nasce {@code false}. */
    private boolean onSale;

    /** Selo de destaque distinto de {@code onSale}. Omitido, nasce {@code false}. */
    private boolean superPromo;

    /** Descrição longa do produto, opcional. Sem limite curto como {@code name}. */
    @Size(max = 5000)
    private String description;

    /** Estágio 01 do admin — link de vídeo cadastrado manualmente, mesma convenção de {@code imageUrl}. */
    @Size(max = 2048)
    private String videoUrl;

    /** Galeria de até 5 imagens ordenadas, opcional. */
    @Size(max = 5)
    private List<@Size(max = 2048) String> images;

    /**
     * Atributos descritivos do próprio produto, opcional. Distintos dos que vão dentro de
     * {@code variants}: aqueles fazem parte do que identifica cada variação da grade, estes só
     * descrevem o item — existem para o produto <b>sem</b> grade, que não tinha onde carregar
     * um "Sabor: Menta".
     */
    @Valid
    @Size(max = 20)
    private List<ProductAttributeRequest> attributes;

    @Valid
    private List<ProductVariantRequest> variants;

    /** Opcional (EST-F019) — omitido, o produto nasce sem precificação. */
    @Valid
    private PricingRequest pricing;

    /** Código de barras/EAN (GTIN-8/12/14), opcional. Único quando informado. */
    @Pattern(regexp = "^[0-9]{8,14}$", message = "barcode deve ter entre 8 e 14 dígitos numéricos")
    private String barcode;

    /** Unidade de medida. Omitido, nasce {@code UN} — mesma semântica de todo produto já cadastrado hoje. */
    private MeasurementUnit unit;

    /** Testador/amostra, distinto de produto padrão da Mahal. Omitido, nasce {@code false}. */
    private boolean sampleProduct;

    /** Elegível a entrar como componente de kit (opt-in). Omitido, nasce {@code false}. */
    private boolean kitComponentEligible;

    /**
     * Aparece no PDV. {@code Boolean} (wrapper), não primitivo — omitido deve resolver para
     * {@code true} (produto nasce visível em todos os canais), e um primitivo tornaria "campo
     * ausente" indistinguível de "false" explícito.
     */
    private Boolean visibleInPos;

    /** Aparece no marketplace/app. Mesma convenção de {@code visibleInPos}: omitido resolve para {@code true}. */
    private Boolean visibleInMarketplace;

    /**
     * Pode ser lançado numa comanda de mesa (PDV-F010). Mesma convenção de {@code visibleInPos}:
     * omitido resolve para {@code true}. Ortogonal a {@code visibleInPos} — bebida e narguilé saem
     * na mesa e no balcão; cigarro e isqueiro, só no balcão.
     */
    private Boolean availableForTable;

    /**
     * Vendido por <b>sessão de mesa</b>, não por unidade (PDV-F010). Omitido, nasce {@code false}:
     * a sessão é a exceção do catálogo, não a regra.
     *
     * <p>Os sabores da sessão são as <b>variações da grade</b> ({@code variants}), e o preço de
     * venda de cada variação é o preço de sessão daquele sabor — não existe campo
     * {@code sessionPrice}.</p>
     */
    private Boolean sessionProduct;

    /**
     * Quantas sessões saem de uma unidade da origem. Só sugestão para o diálogo de conversão de
     * estoque do admin — <b>não movimenta saldo sozinho</b>.
     */
    @Positive(message = "sessionsPerUnit deve ser maior que zero")
    private Integer sessionsPerUnit;

    /**
     * Consumo livre (<i>open rosh</i>): valor fixo cobrado <b>uma única vez</b> por sessão, com
     * trocas de sabor ilimitadas. Nulo quando o produto não oferece consumo livre.
     */
    @PositiveOrZero(message = "openRoshPrice não pode ser negativo")
    private BigDecimal openRoshPrice;

    /** PDV-F048 — faixa da sessão sugerida no PDV para esta essência. Nulo = sem faixa. */
    private Long sessionTierId;

    /**
     * {@code SIMPLES} ou {@code KIT}. Omitido, nasce {@code SIMPLES} — comportamento anterior a
     * EST-F023 preservado. {@code KIT} não pode vir com {@code variants} nem com
     * {@code initialStock}: kit não tem grade nem saldo próprio.
     */
    private ProductType type;

    /**
     * Estoque inicial, opcional (EST-F023). Quando informado, a entrada é registrada na MESMA
     * transação da criação — evita o estado "produto criado, estoque não" das duas chamadas
     * separadas que o frontend fazia antes.
     */
    @Valid
    private InitialStockRequest initialStock;

    /**
     * Receita do kit, opcional, só considerada quando {@code type == KIT}. Ausente ou vazia: kit
     * nasce sem receita, do jeito que já funcionava antes — ainda precisa de
     * {@code PUT .../kit} depois. Presente e não vazia: a receita é validada e gravada na MESMA
     * transação da criação do produto, fechando a janela de "kit órfão sem receita" que existia
     * com o fluxo em duas chamadas (POST seguido de PUT).
     */
    @Valid
    @Size(max = 100)
    private List<KitComponentRequest> components;

    /**
     * Status de publicação (EST-F023). Omitido, nasce {@code ATIVO} — comportamento anterior a
     * esta feature preservado. {@code RASCUNHO} deixa o produto/kit fora das vitrines e listagens
     * que filtram por publicado, sem exigir nenhum campo além de {@code sku}/{@code name}, e
     * conta contra o teto de 5 rascunhos (validado no servidor, 409 {@code DRAFT_LIMIT_REACHED}
     * no 6º).
     */
    private ProductStatus status;

    /**
     * Indica se este request mexe em preço em <b>qualquer</b> nível — na raiz ou dentro de alguma
     * variação (EST-F020).
     *
     * <p>Existe para o {@code @PreAuthorize} do controller. Antes de haver preço por variação, a
     * expressão checava {@code #request.pricing == null} e isso bastava. Com o preço podendo vir
     * dentro de {@code variants[]}, aquela checagem virou um furo: quem tem apenas
     * {@code ESTOQUE_PRODUCT_MANAGE} passaria a precificar pela porta lateral, desfazendo a
     * separação que EST-F019 criou de propósito entre manter cadastro e mexer em preço.</p>
     */
    public boolean touchesPricing() {
        if (pricing != null) {
            return true;
        }
        return variants != null && variants.stream().anyMatch(v -> v != null && v.getPricing() != null);
    }

    /**
     * Indica se este request lança movimentação de estoque (EST-F023). Mesmo raciocínio de
     * {@link #touchesPricing()}: sem esta checagem, quem só tem {@code ESTOQUE_PRODUCT_MANAGE}
     * (editor de catálogo) poderia lançar estoque pela porta lateral da criação de produto, sem
     * precisar de {@code ESTOQUE_STOCK_MANAGE}.
     */
    public boolean touchesStock() {
        return initialStock != null;
    }
}
