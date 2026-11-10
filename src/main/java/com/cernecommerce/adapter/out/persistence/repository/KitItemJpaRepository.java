package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.KitItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KitItemJpaRepository extends JpaRepository<KitItemEntity, Long> {

    List<KitItemEntity> findByKitProductIdOrderByPositionAsc(Long kitProductId);
}
