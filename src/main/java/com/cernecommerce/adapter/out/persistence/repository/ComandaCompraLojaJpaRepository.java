package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ComandaCompraLojaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ComandaCompraLojaJpaRepository
        extends JpaRepository<ComandaCompraLojaEntity, ComandaCompraLojaEntity.Id> {

    List<ComandaCompraLojaEntity> findByComandaIdOrderByRegistradoEmAsc(Long comandaId);

    List<ComandaCompraLojaEntity> findByComandaIdInOrderByRegistradoEmAsc(Collection<Long> comandaIds);
}
