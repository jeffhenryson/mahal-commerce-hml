package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Item de uma {@link ComandaEntity} (PDV-F009). Tabela {@code comanda_item}.
 *
 * <p>{@code costPrice} é anulável — produto sem custo conhecido no lançamento fica nulo, nunca
 * zero, mesma convenção de {@code order_item.cost_price}.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "comanda_item")
public class ComandaItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comanda_id", nullable = false, foreignKey = @ForeignKey(name = "fk_comanda_item_comanda"))
    @ToString.Exclude
    private ComandaEntity comanda;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    // PDV-F010 — por que a linha existe. Sem @Enumerated: mesma convenção enum-como-string de
    // product.type/product.unit.
    @Column(nullable = false, length = 20)
    private String mode;

    // Linha a preço zero que AINDA baixa estoque (promo "pague 1 leve 2", troca de open rosh).
    // Campo próprio, e não inferido de unit_price = 0: um desconto de 100% dá o mesmo zero.
    @Column(nullable = false)
    private boolean courtesy;

    // Liga o segundo sabor (ou a troca) à linha de origem, na mesma comanda.
    @Column(name = "linked_item_id")
    private Long linkedItemId;

    // PDV-F011 — registro livre do setup da mesa (narguilé, filtro, qual pinça). Texto opaco: o
    // servidor grava e devolve, nunca interpreta. É o que dá casa à pinça, que não pode virar
    // cortesia — cortesia baixa estoque, e a pinça não é consumida.
    @Column(length = 200)
    private String notes;

    // PDV-F011 — parcela de unit_price que veio de acréscimo manual no open rosh. Guardada à parte
    // porque não dá para reconstruí-la do unit_price, que já é a soma; mesma razão de
    // discount_amount ser campo próprio em order_item em vez de virar um preço menor.
    @Column(name = "surcharge_amount", precision = 14, scale = 2)
    private BigDecimal surchargeAmount;

    /**
     * PDV-F017 — o pedido que cobrou esta linha. Nulo é a linha em aberto, e é o que a comanda soma
     * no {@code runningTotal}. Não é FK gerenciada por associação de propósito: a linha aponta para
     * um pedido, mas o pedido não é dono dela, e um {@code @ManyToOne} traria o agregado de vendas
     * para dentro do de mesa.
     */
    @Column(name = "closed_in_order_id")
    private Long closedInOrderId;

    /**
     * EST-F027 — qual uso da lata esta linha foi ("a 3ª de 5"), congelado no lançamento.
     *
     * <p>Snapshot, não referência à lata: o histórico continua verdadeiro depois que ela for
     * reposta, e a tela mostra o contador por linha sem uma segunda chamada. Nulo é a linha que
     * baixou uma <b>unidade</b> — toda linha anterior à V124, e toda linha de produto que não é
     * vendido por sessão —, e é essa diferença que o cancelamento consulta para decidir entre
     * decrementar o contador e devolver unidade ao estoque.</p>
     */
    @Column(name = "package_uses")
    private Integer packageUses;

    @Column(name = "package_sessions_per_unit")
    private Integer packageSessionsPerUnit;

    // PDV-F019 — kit montável: o pacote, o modelo e a parte do desconto do kit desta linha.
    @Column(name = "kit_bundle_id", length = 36)
    private String kitBundleId;

    @Column(name = "kit_template_id")
    private Long kitTemplateId;

    @Column(name = "kit_discount_amount", precision = 14, scale = 2)
    private BigDecimal kitDiscountAmount;

    // PDV-F023 — status e tempo de mesa da sessão. Nulos em linha de catálogo.
    @Column(name = "session_status", length = 20)
    private String sessionStatus;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "collected_at")
    private Instant collectedAt;

    // PDV-F034 — sessão que foi ao salão antes de paga.
    @Column(name = "pay_later", nullable = false)
    private boolean payLater;

    // PDV-F024 — carvão (só registro) e adicionais cobrados na sessão.
    @Column(length = 10)
    private String charcoal;

    // PDV-F027 — sessão no vaso grande, para "repetir sessão" refazer a mesma configuração.
    @Column(name = "vaso_grande", nullable = false)
    private boolean vasoGrande;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    @OrderBy("id")
    @ToString.Exclude
    private List<ComandaItemAddonEntity> addons = new ArrayList<>();
}
