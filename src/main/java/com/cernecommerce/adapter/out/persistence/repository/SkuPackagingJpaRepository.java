package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SkuPackagingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkuPackagingJpaRepository extends JpaRepository<SkuPackagingEntity, String> {

    List<SkuPackagingEntity> findByParentSkuOrderByChildSkuAsc(String parentSku);
}
