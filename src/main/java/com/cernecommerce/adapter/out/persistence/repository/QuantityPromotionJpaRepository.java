package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.QuantityPromotionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuantityPromotionJpaRepository extends JpaRepository<QuantityPromotionEntity, Long> {

    List<QuantityPromotionEntity> findAllByOrderByIdAsc();

    List<QuantityPromotionEntity> findByActiveTrueOrderByIdAsc();

    @Query("SELECT p FROM QuantityPromotionEntity p WHERE LOWER(p.name) = LOWER(:name)")
    Optional<QuantityPromotionEntity> findByNameIgnoringCase(@Param("name") String name);
}
