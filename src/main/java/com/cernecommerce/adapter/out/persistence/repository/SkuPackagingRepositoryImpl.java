package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.SkuPackagingEntity;
import com.cernecommerce.core.domain.model.estoque.SkuPackaging;
import com.cernecommerce.core.ports.out.estoque.SkuPackagingRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class SkuPackagingRepositoryImpl implements SkuPackagingRepository {

    private final SkuPackagingJpaRepository jpaRepository;

    public SkuPackagingRepositoryImpl(SkuPackagingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SkuPackaging> findByChild(String childSku) {
        return jpaRepository.findById(childSku).map(SkuPackagingRepositoryImpl::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkuPackaging> findByParent(String parentSku) {
        return jpaRepository.findByParentSkuOrderByChildSkuAsc(parentSku).stream()
                .map(SkuPackagingRepositoryImpl::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkuPackaging> findAll() {
        return jpaRepository.findAll().stream().map(SkuPackagingRepositoryImpl::toDomain).toList();
    }

    @Override
    public SkuPackaging save(SkuPackaging packaging) {
        return toDomain(jpaRepository.save(new SkuPackagingEntity(packaging.childSku(), packaging.parentSku(),
                packaging.unitsPerParent())));
    }

    @Override
    public void deleteByChild(String childSku) {
        jpaRepository.deleteById(childSku);
    }

    private static SkuPackaging toDomain(SkuPackagingEntity e) {
        return new SkuPackaging(e.getChildSku(), e.getParentSku(), e.getUnitsPerParent());
    }
}
