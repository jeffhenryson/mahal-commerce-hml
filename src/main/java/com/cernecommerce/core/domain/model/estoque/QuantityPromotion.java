package com.cernecommerce.core.domain.model.estoque;

import com.cernecommerce.core.domain.exception.estoque.InvalidPromotionPeriodException;
import com.cernecommerce.core.domain.exception.estoque.InvalidPromotionPriceException;
import com.cernecommerce.core.domain.exception.estoque.InvalidPromotionQuantityException;
import com.cernecommerce.core.domain.model.Money;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Promoção por quantidade ("leve 4 por R$ 10"): um grupo de {@code quantity} unidades de qualquer
 * combinação dos {@code skus} sai por {@code bundlePrice}. É uma <b>regra de preço</b> sobre os
 * produtos reais, aplicada pelo servidor ({@link QuantityPromotionEngine}). Não tem SKU nem saldo
 * próprio, e cada unidade baixa do estoque do próprio SKU.
 *
 * <p>Diferente do kit com receita (um SKU que o operador escolhe) e do kit montável (desconto em %
 * montado à mão): ninguém escolhe a promoção, ela vale sozinha quando o carrinho fecha o grupo.</p>
 *
 * <p>{@code bundlePrice} maior ou igual ao preço normal do grupo <b>não</b> é inválido. A regra só não
 * aplica, porque a promoção nunca encarece.</p>
 */
public record QuantityPromotion(
        Long id,
        String name,
        String description,
        int quantity,
        BigDecimal bundlePrice,
        List<String> skus,
        Set<PromotionChannel> channels,
        boolean active,
        Instant startsAt,
        Instant endsAt,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static final int NAME_MAX_LENGTH = 150;
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    public QuantityPromotion {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        name = name.trim();
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("name excede " + NAME_MAX_LENGTH + " caracteres");
        }
        description = description == null || description.isBlank() ? null : description.trim();
        if (quantity < 2) {
            throw new InvalidPromotionQuantityException(quantity);
        }
        if (bundlePrice == null || bundlePrice.signum() <= 0) {
            throw new InvalidPromotionPriceException(bundlePrice);
        }
        bundlePrice = Money.of(bundlePrice);
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new InvalidPromotionPeriodException();
        }
        if (skus == null || skus.isEmpty()) {
            throw new IllegalArgumentException("a promoção precisa de pelo menos um SKU");
        }
        Set<String> unicos = new LinkedHashSet<>();
        for (String sku : skus) {
            if (sku != null && !sku.isBlank()) {
                unicos.add(sku.trim());
            }
        }
        if (unicos.isEmpty()) {
            throw new IllegalArgumentException("a promoção precisa de pelo menos um SKU");
        }
        skus = List.copyOf(unicos);
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("a promoção precisa de pelo menos um canal");
        }
        channels = Set.copyOf(EnumSet.copyOf(channels));
    }

    /** Situação no instante: inativa pelo flag, agendada antes do início, encerrada a partir do fim. */
    public PromotionStatus status(Instant now) {
        if (!active) {
            return PromotionStatus.INATIVA;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return PromotionStatus.AGENDADA;
        }
        if (endsAt != null && !now.isBefore(endsAt)) {
            return PromotionStatus.ENCERRADA;
        }
        return PromotionStatus.ATIVA;
    }

    /** Vale neste canal agora? {@code active}, {@code startsAt <= now < endsAt} e canal na lista. */
    public boolean isVigente(Instant now, PromotionChannel channel) {
        return status(now) == PromotionStatus.ATIVA && channels.contains(channel);
    }

    public QuantityPromotion withId(Long newId) {
        return new QuantityPromotion(newId, name, description, quantity, bundlePrice, skus, channels, active,
                startsAt, endsAt, createdAt, createdBy, updatedAt, updatedBy);
    }

    public QuantityPromotion withActive(boolean newActive) {
        return new QuantityPromotion(id, name, description, quantity, bundlePrice, skus, channels, newActive,
                startsAt, endsAt, createdAt, createdBy, updatedAt, updatedBy);
    }

    /** Mesma regra com outra lista de SKUs: a venda filtra os que deixaram de ser vendáveis. */
    public QuantityPromotion withSkus(List<String> newSkus) {
        return new QuantityPromotion(id, name, description, quantity, bundlePrice, newSkus, channels, active,
                startsAt, endsAt, createdAt, createdBy, updatedAt, updatedBy);
    }

    public QuantityPromotion withAudit(Instant newCreatedAt, String newCreatedBy, Instant newUpdatedAt,
            String newUpdatedBy) {
        return new QuantityPromotion(id, name, description, quantity, bundlePrice, skus, channels, active,
                startsAt, endsAt, newCreatedAt, newCreatedBy, newUpdatedAt, newUpdatedBy);
    }

    /** Selo curto da vitrine e do PDV, igual ao {@code seloPromocao} do front: "4 por R$ 10". */
    public String label() {
        BigDecimal semZeros = bundlePrice.stripTrailingZeros();
        String preco = semZeros.scale() <= 0
                ? semZeros.toPlainString()
                : bundlePrice.toPlainString().replace('.', ',');
        return quantity + " por R$ " + preco;
    }
}
