package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductCategoryEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.Category;
import com.cernecommerce.core.ports.out.estoque.CategoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class CategoryRepositoryImpl implements CategoryRepository {

    private final ProductCategoryJpaRepository jpaRepository;

    public CategoryRepositoryImpl(ProductCategoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Category save(Category category) {
        ProductCategoryEntity entity = new ProductCategoryEntity();
        entity.setId(category.id());
        entity.setName(category.name());
        entity.setFeatured(category.featured());
        entity.setDisplayOrder(category.displayOrder());
        entity.setActive(category.active());
        entity.setCigarette(category.cigarette());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Category> findByName(String name) {
        return jpaRepository.findByNameIgnoringCase(name).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Category> findAll(int page, int size) {
        Page<ProductCategoryEntity> result =
                jpaRepository.findAllByOrderByFeaturedDescDisplayOrderAscNameAsc(PageRequest.of(page, size));
        return new PageResult<>(result.getContent().stream().map(this::toDomain).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findActiveOrdered() {
        return jpaRepository.findByActiveTrueOrderByFeaturedDescDisplayOrderAscNameAsc().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Category> findCigarette() {
        return jpaRepository.findByCigaretteTrue().stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    private Category toDomain(ProductCategoryEntity e) {
        return Category.of(e.getId(), e.getName(), e.isFeatured(), e.getDisplayOrder(), e.isActive(),
                e.isCigarette());
    }
}
