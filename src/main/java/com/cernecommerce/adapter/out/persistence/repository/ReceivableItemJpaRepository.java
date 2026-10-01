package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ReceivableItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ReceivableItemJpaRepository extends JpaRepository<ReceivableItemEntity, Long> {

    List<ReceivableItemEntity> findByReceivableIdInOrderByIdAsc(Collection<Long> receivableIds);
}
