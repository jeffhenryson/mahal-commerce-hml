package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Item de um {@link OrderEntity} (PDV-F004). Tabela {@code order_item}, renomeada de
 * {@code sale_item} pela V65.
 *
 * <p>{@code costPrice} e {@code cashbackPercent} são anuláveis <b>só</b> para os itens anteriores à
 * migração: um default zero mentiria sobre a margem e sobre o cashback gerado.</p>
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "order_item")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_item_order"))
    @ToString.Exclude
    private OrderEntity order;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_price", precision = 14, scale = 2)
    private BigDecimal costPrice;

    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "cashback_percent", precision = 9, scale = 4)
    private BigDecimal cashbackPercent;

    // Nome do produto congelado no instante da venda — mesma razão de cost_price: se o produto for
    // renomeado depois, o histórico do pedido não pode mudar junto. Nulo para itens anteriores a
    // esta migration.
    @Column(name = "product_name", length = 255)
    private String productName;

    // PDV-F010 — modo e cortesia viajam do ComandaItem para cá no fechamento da comanda. Sem eles,
    // o histórico da mesa no pedido não distingue cortesia de item cobrado.
    @Column(nullable = false, length = 20)
    private String mode;

    @Column(nullable = false)
    private boolean courtesy;

    // PDV-F011 — notes e surcharge_amount atravessam o fechamento pela mesma razão que mode e
    // courtesy: a pergunta "qual pinça saiu com aquela mesa" é feita depois de a mesa ter fechado.
    // Nulos em toda venda que não veio de mesa.
    @Column(length = 200)
    private String notes;

    @Column(name = "surcharge_amount", precision = 14, scale = 2)
    private BigDecimal surchargeAmount;

    // PDV-F024 — carvão da sessão (CUBO/JUMBO), só registro. Nulo fora de sessão do cardápio.
    @Column(length = 10)
    private String charcoal;

    // Promoção por quantidade: detalhamento de discount_amount. promotion_id sem FK (a regra pode ser excluída).
    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "promotion_name", length = 150)
    private String promotionName;

    @Column(name = "promotion_discount_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal promotionDiscountAmount = BigDecimal.ZERO;

    // V198 — a linha era de categoria fora dos benefícios no instante da venda.
    @Column(name = "benefits_excluded", nullable = false)
    private boolean benefitsExcluded;
}
