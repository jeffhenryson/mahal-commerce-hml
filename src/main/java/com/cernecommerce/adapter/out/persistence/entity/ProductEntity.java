package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product", uniqueConstraints = @UniqueConstraint(name = "uk_product_sku", columnNames = "sku"))
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 100)
    private String category;

    @Column(length = 100)
    private String brand;

    // Estágio 01 do admin — link de imagem cadastrado manualmente pelo lojista, não upload.
    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    // Estágio 01 do admin — produto em promoção; o marketplace filtra por este campo.
    @Column(name = "on_sale", nullable = false)
    private boolean onSale;

    // Selo de destaque distinto de onSale — segundo nível de promoção, sem filtro de query
    // próprio (diferente de onSale, que é parâmetro de GET /shop/catalog).
    @Column(name = "super_promo", nullable = false)
    private boolean superPromo;

    // Descrição longa do produto, sem limite curto como name. Sem @Lob: TEXT é a convenção já
    // usada no projeto (ver AuditLogEntity, CustomerNoteEntity, NotificationEntity).
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    // Estágio 01 do admin — link de vídeo cadastrado manualmente, mesma convenção de imageUrl.
    @Column(name = "video_url", length = 2048)
    private String videoUrl;

    @Column(nullable = false)
    private boolean active;

    // EST-F019 — precificação. Nullable: produto não precificado é estado válido, e preço zero
    // não é o mesmo que preço desconhecido. Ver V63 e o value object Pricing.
    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "markup_percent", precision = 9, scale = 4)
    private BigDecimal markupPercent;

    @Column(name = "sale_price", precision = 14, scale = 2)
    private BigDecimal salePrice;

    // Preço "de/por" — puramente valor de exibição, sem checagem relacional contra salePrice
    // (ver Pricing.hasDiscount, que decide na leitura se o desconto exibido faz sentido).
    @Column(name = "original_price", precision = 14, scale = 2)
    private BigDecimal originalPrice;

    // EST-F015 (Fatia 6) — SIMPLES ou KIT. Sem @Enumerated: mesma convenção enum-como-string do
    // resto do projeto (ver OrderEntity.status), conversão manual em ProductRepositoryImpl.
    @Column(nullable = false, length = 20)
    private String type;

    // EST-F008 — opt-in de rastreamento de lote/validade. Kit nunca é lote-rastreado (invariante
    // no domínio, não no schema — mesma régua de outras regras entre campos deste projeto).
    @Column(name = "lot_tracked", nullable = false)
    private boolean lotTracked;

    // EST-F036 — a base de um produto com variações é vendável? Só tem efeito com variações (V149).
    @Column(name = "parent_sellable", nullable = false)
    private boolean parentSellable;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @ToString.Exclude
    private List<ProductVariantEntity> variants = new ArrayList<>();

    // Galeria de até 5 imagens ordenadas. @ElementCollection (mesmo padrão leve de
    // ProductAttribute/product_attribute) em vez de entidade completa: não há requisito de CRUD
    // independente por imagem, só substituição da lista inteira via PATCH.
    @ElementCollection
    @CollectionTable(name = "product_image", joinColumns = @JoinColumn(name = "product_id",
            foreignKey = @ForeignKey(name = "fk_product_image_product")))
    @OrderColumn(name = "image_order")
    @Column(name = "url", length = 2048)
    private List<String> images = new ArrayList<>();

    // Atributos descritivos do próprio SKU pai, para o produto sem grade poder carregar
    // "Sabor: Menta" — antes só existia atributo dentro de variação.
    //
    // Tabela NOVA em vez de tornar product_attribute.variant_id anulável: aquela tabela é a que
    // distingue uma variação das outras, tem FK e índice para variant_id, e admitir linha sem
    // variação ali significaria uma coluna nula em metade das linhas mais um CHECK para garantir
    // que exatamente um dos dois donos está preenchido. Duas tabelas com um dono cada saem mais
    // baratas e mantêm a FK existente intacta.
    //
    // O ProductAttributeEmbeddable é reaproveitado como está: as colunas attr_type/attr_value são
    // as mesmas, só muda a tabela e a coluna de junção.
    @ElementCollection
    @CollectionTable(name = "product_root_attribute", joinColumns = @JoinColumn(name = "product_id",
            foreignKey = @ForeignKey(name = "fk_product_root_attribute_product")))
    private List<ProductAttributeEmbeddable> attributes = new ArrayList<>();

    // Vínculo opcional com product_category. Guardado como id solto e não como @ManyToOne: o
    // agregado Product não precisa navegar até a categoria (o nome já vem denormalizado na coluna
    // "category"), e um @ManyToOne traria a entidade inteira em toda leitura de produto sem
    // ninguém pedir. Nulo = produto ainda não vinculado, estado válido para o legado.
    @Column(name = "category_id")
    private Long categoryId;

    // Vínculo opcional com product_brand. Mesmo raciocínio de categoryId: guardado como id solto,
    // não @ManyToOne — o nome já vem denormalizado na coluna "brand". Nulo = produto ainda não
    // vinculado, estado válido para o legado.
    @Column(name = "brand_id")
    private Long brandId;

    // Código de barras/EAN — GTIN-8/12/14, texto para preservar zeros à esquerda. Único quando
    // informado (índice parcial em V91, não constraint de coluna, porque é opcional).
    @Column(name = "barcode", length = 14)
    private String barcode;

    // Unidade de medida. Sem @Enumerated: mesma convenção enum-como-string já usada em "type".
    @Column(name = "unit", nullable = false, length = 10)
    private String unit;

    // Testador/amostra, ortogonal a "type" (SIMPLES/KIT) — controla se o campo de estoque mínimo
    // de reposição faz sentido no formulário do admin.
    @Column(name = "sample_product", nullable = false)
    private boolean sampleProduct;

    // Elegível a entrar como componente de kit (opt-in) — checado em EstoqueService.defineKitRecipe.
    @Column(name = "kit_component_eligible", nullable = false)
    private boolean kitComponentEligible;

    // Visibilidade por canal — produto pode existir só no PDV (balcão) ou só no marketplace, sem
    // precisar inativar o cadastro inteiro.
    @Column(name = "visible_in_pos", nullable = false)
    private boolean visibleInPos;

    @Column(name = "visible_in_marketplace", nullable = false)
    private boolean visibleInMarketplace;

    // PDV-F010 — mesa e sessão de narguilé. available_for_table é ortogonal a visible_in_pos:
    // bebida e narguilé saem na mesa e no balcão, cigarro e isqueiro só no balcão.
    @Column(name = "available_for_table", nullable = false)
    private boolean availableForTable;

    // Vendido por sessão de mesa, não por unidade. Os sabores são as variações da grade.
    @Column(name = "session_product", nullable = false)
    private boolean sessionProduct;

    // Quantas sessões saem de uma unidade da origem — sugestão do diálogo de conversão de estoque
    // do admin. Nullable: não movimenta saldo sozinho, e a maioria dos produtos não é sessão.
    @Column(name = "sessions_per_unit")
    private Integer sessionsPerUnit;

    // Consumo livre por valor fixo, cobrado uma vez por sessão. Mora no SKU pai de propósito —
    // ver ComandaService.addItem, que resolve OPEN_ROSH por aqui e não pelo preço da variação.
    @Column(name = "open_rosh_price", precision = 14, scale = 2)
    private BigDecimal openRoshPrice;

    // Preço extraordinário — valor adicional destinado a uma causa, por fora do preço de venda.
    // Puramente informativo (ver Pricing.causeAmount); nullable pelo mesmo motivo dos demais
    // campos de precificação.
    @Column(name = "cause_amount", precision = 14, scale = 2)
    private BigDecimal causeAmount;

    // EST-F023 — RASCUNHO ou ATIVO. Sem @Enumerated: mesma convenção enum-como-string de "type"/
    // "unit". DEFAULT 'ATIVO' na migration cobre o backfill do catálogo existente.
    @Column(nullable = false, length = 20)
    private String status;
}
