package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;

import java.util.List;
import java.util.Optional;

/** Persistência das promoções por quantidade. */
public interface QuantityPromotionRepository {

    /** Cria ou substitui a regra inteira (SKUs e canais incluídos). */
    QuantityPromotion save(QuantityPromotion promotion);

    Optional<QuantityPromotion> findById(Long id);

    /** Todas, por id. */
    List<QuantityPromotion> findAll();

    /** Só as com {@code active = true}. Quem chama filtra período e canal no domínio. */
    List<QuantityPromotion> findAllActive();

    /** Comparação sem caixa. */
    Optional<QuantityPromotion> findByNameIgnoreCase(String name);

    void deleteById(Long id);
}
