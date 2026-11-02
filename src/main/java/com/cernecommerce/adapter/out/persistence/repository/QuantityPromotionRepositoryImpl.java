package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.QuantityPromotionEntity;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.ports.out.estoque.QuantityPromotionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@Transactional
public class QuantityPromotionRepositoryImpl implements QuantityPromotionRepository {

    private final QuantityPromotionJpaRepository jpaRepository;

    public QuantityPromotionRepositoryImpl(QuantityPromotionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public QuantityPromotion save(QuantityPromotion p) {
        QuantityPromotionEntity entity = p.id() == null ? new QuantityPromotionEntity()
                : jpaRepository.findById(p.id()).orElseGet(QuantityPromotionEntity::new);
        entity.setName(p.name());
        entity.setDescription(p.description());
        entity.setQuantity(p.quantity());
        entity.setBundlePrice(p.bundlePrice());
        entity.setActive(p.active());
        entity.setStartsAt(p.startsAt());
        entity.setEndsAt(p.endsAt());
        entity.setCreatedAt(p.createdAt());
        entity.setCreatedBy(p.createdBy());
        entity.setUpdatedAt(p.updatedAt());
        entity.setUpdatedBy(p.updatedBy());
        replace(entity.getSkus(), new HashSet<>(p.skus()));
        replace(entity.getChannels(), new HashSet<>(p.channels()));
        return toDomain(jpaRepository.saveAndFlush(entity));
    }

    private static <T> void replace(Set<T> current, Set<T> incoming) {
        current.retainAll(incoming);
        current.addAll(incoming);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuantityPromotion> findById(Long id) {
        return jpaRepository.findById(id).map(QuantityPromotionRepositoryImpl::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuantityPromotion> findAll() {
        return jpaRepository.findAllByOrderByIdAsc().stream().map(QuantityPromotionRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuantityPromotion> findAllActive() {
        return jpaRepository.findByActiveTrueOrderByIdAsc().stream()
                .map(QuantityPromotionRepositoryImpl::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuantityPromotion> findByNameIgnoreCase(String name) {
        return jpaRepository.findByNameIgnoringCase(name).map(QuantityPromotionRepositoryImpl::toDomain);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    static QuantityPromotion toDomain(QuantityPromotionEntity e) {
        return new QuantityPromotion(e.getId(), e.getName(), e.getDescription(), e.getQuantity(), e.getBundlePrice(),
                e.getSkus().stream().sorted().toList(), Set.copyOf(e.getChannels()), e.isActive(), e.getStartsAt(),
                e.getEndsAt(), e.getCreatedAt(), e.getCreatedBy(), e.getUpdatedAt(), e.getUpdatedBy());
    }
}
