package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "product_variant", uniqueConstraints = @UniqueConstraint(name = "uk_product_variant_sku", columnNames = "sku"))
public class ProductVariantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(name = "fk_product_variant_product"))
    @ToString.Exclude
    private ProductEntity product;

    @Column(nullable = false, length = 50, unique = true)
    private String sku;

    @Column(nullable = false)
    private boolean active;

    // Carregados em lote (um SELECT … IN por até 100 variações), não por JOIN FETCH: buscar junto
    // com p.variants, que é List, duplicava a variação para cada atributo dela.
    @ElementCollection
    @BatchSize(size = 100)
    @CollectionTable(name = "product_attribute", joinColumns = @JoinColumn(name = "variant_id",
            foreignKey = @ForeignKey(name = "fk_product_attribute_variant")))
    private Set<ProductAttributeEmbeddable> attributes = new LinkedHashSet<>();

    // EST-F020: precificação própria da variação. Todas nulas = herda do pai, que é o padrão.
    // Não são embutidas num @Embedded Pricing porque o domínio precisa distinguir "não tem preço
    // próprio" (o record inteiro nulo) de "tem, mas desconhecido" — e um @Embedded com todos os
    // campos nulos é materializado pelo Hibernate como objeto não-nulo, apagando a distinção.
    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "markup_percent", precision = 9, scale = 4)
    private BigDecimal markupPercent;

    @Column(name = "sale_price", precision = 14, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "original_price", precision = 14, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "cause_amount", precision = 14, scale = 2)
    private BigDecimal causeAmount;

    // Código de barras/EAN próprio da variação — cada SKU filho da grade tem o seu. Único quando
    // informado (índice parcial em V91).
    @Column(name = "barcode", length = 14)
    private String barcode;
}
