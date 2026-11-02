package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "quantity_promotion")
public class QuantityPromotionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "bundle_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal bundlePrice;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 80)
    private String updatedBy;

    // Set e não List: as duas coleções vêm juntas (EAGER) e duas List gerariam MultipleBagFetchException.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quantity_promotion_sku", joinColumns = @JoinColumn(name = "promotion_id"))
    @Column(name = "sku", nullable = false, length = 50)
    @ToString.Exclude
    private Set<String> skus = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quantity_promotion_channel", joinColumns = @JoinColumn(name = "promotion_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @ToString.Exclude
    private Set<PromotionChannel> channels = new HashSet<>();
}
