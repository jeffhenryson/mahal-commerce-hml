package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductRequestEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRequestEventJpaRepository extends JpaRepository<ProductRequestEventEntity, Long> {

    List<ProductRequestEventEntity> findByRequestIdOrderByOccurredAtDescIdDesc(Long requestId);
}
