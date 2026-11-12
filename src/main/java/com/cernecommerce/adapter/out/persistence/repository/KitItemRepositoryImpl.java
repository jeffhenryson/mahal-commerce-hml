package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.KitItemEntity;
import com.cernecommerce.core.domain.model.estoque.KitItem;
import com.cernecommerce.core.ports.out.estoque.KitItemRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
@Transactional
public class KitItemRepositoryImpl implements KitItemRepository {

    private final KitItemJpaRepository jpaRepository;

    public KitItemRepositoryImpl(KitItemJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<KitItem> findByKitProductId(Long kitProductId) {
        return jpaRepository.findByKitProductIdOrderByPositionAsc(kitProductId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<KitItem> replaceAll(Long kitProductId, List<KitItem> items) {
        Map<Long, KitItemEntity> current = jpaRepository.findByKitProductIdOrderByPositionAsc(kitProductId).stream()
                .collect(Collectors.toMap(KitItemEntity::getId, Function.identity()));
        Set<Long> kept = items.stream().map(KitItem::id).filter(id -> id != null).collect(Collectors.toSet());
        List<KitItemEntity> removed = current.values().stream().filter(e -> !kept.contains(e.getId())).toList();
        jpaRepository.deleteAll(removed);

        List<KitItemEntity> toSave = items.stream().map(item -> {
            // id de outro kit (ou inexistente) não é atualizado: vira item novo deste kit.
            KitItemEntity entity = item.id() != null && current.containsKey(item.id())
                    ? current.get(item.id()) : new KitItemEntity();
            entity.setKitProductId(kitProductId);
            entity.setName(item.name());
            entity.setQuantity(item.quantity());
            entity.setUnitCost(item.unitCost());
            entity.setCategoryId(item.categoryId());
            entity.setBrandId(item.brandId());
            entity.setDescription(item.description());
            entity.setImageUrl(item.imageUrl());
            entity.setPosition(item.position());
            return entity;
        }).toList();
        return jpaRepository.saveAll(toSave).stream().map(this::toDomain).toList();
    }

    private KitItem toDomain(KitItemEntity e) {
        return new KitItem(e.getId(), e.getKitProductId(), e.getName(), e.getQuantity(), e.getUnitCost(),
                e.getCategoryId(), null, e.getBrandId(), null, e.getDescription(), e.getImageUrl(), e.getPosition());
    }
}
