package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SalaoEssenciaPerfilConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SalaoEssenciaPerfilConfigJpaRepository
        extends JpaRepository<SalaoEssenciaPerfilConfigEntity, String> {

    List<SalaoEssenciaPerfilConfigEntity> findBySkuIn(Collection<String> skus);
}
