package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.ProductCategoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductCategoryJpaRepository extends JpaRepository<ProductCategoryEntity, Long> {

    // Case-insensitive: o admin ainda manda a categoria como texto livre no cadastro do produto,
    // e "Narguilé"/"narguilé" não podem virar duas categorias. O índice funcional criado na V90
    // (LOWER(name)) é o que sustenta esta consulta.
    @Query("SELECT c FROM ProductCategoryEntity c WHERE LOWER(c.name) = LOWER(:name)")
    Optional<ProductCategoryEntity> findByNameIgnoringCase(@Param("name") String name);

    Page<ProductCategoryEntity> findAllByOrderByFeaturedDescDisplayOrderAscNameAsc(Pageable pageable);

    List<ProductCategoryEntity> findByActiveTrueOrderByFeaturedDescDisplayOrderAscNameAsc();

    List<ProductCategoryEntity> findByCigaretteTrue();
}
