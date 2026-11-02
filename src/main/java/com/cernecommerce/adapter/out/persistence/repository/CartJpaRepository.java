package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CartEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CartJpaRepository extends JpaRepository<CartEntity, Long> {

    @Query("SELECT DISTINCT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.customerId = :customerId")
    Optional<CartEntity> findByCustomerId(Long customerId);

    /**
     * Página de ids de carrinhos com ao menos uma linha, mais recente primeiro. Paginar ids e só
     * depois buscar com {@code JOIN FETCH} evita a paginação em memória do Hibernate (HHH90003004).
     */
    @Query(value = "SELECT c.id FROM CartEntity c WHERE EXISTS (SELECT 1 FROM CartItemEntity i WHERE i.cart = c)"
            + " ORDER BY c.updatedAt DESC, c.id DESC",
            countQuery = "SELECT COUNT(c) FROM CartEntity c WHERE EXISTS (SELECT 1 FROM CartItemEntity i WHERE i.cart = c)")
    Page<Long> findNonEmptyIds(Pageable pageable);

    @Query("SELECT DISTINCT c FROM CartEntity c LEFT JOIN FETCH c.items WHERE c.id IN :ids")
    List<CartEntity> findWithItemsByIdIn(@Param("ids") Collection<Long> ids);
}
