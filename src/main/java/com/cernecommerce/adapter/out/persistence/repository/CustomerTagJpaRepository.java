package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerTagEntity;
import com.cernecommerce.adapter.out.persistence.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerTagJpaRepository extends JpaRepository<CustomerTagEntity, CustomerTagEntity.Id> {

    boolean existsByCustomerIdAndTagId(Long customerId, Long tagId);

    @Modifying
    void deleteByCustomerIdAndTagId(Long customerId, Long tagId);

    /** V206 — tira todas as tags do cliente, a VIP inclusive (exclusão do perfil). */
    @Modifying
    @Query("DELETE FROM CustomerTagEntity ct WHERE ct.customerId = :customerId")
    int deleteAllByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT t FROM TagEntity t JOIN CustomerTagEntity ct ON ct.tagId = t.id "
            + "WHERE ct.customerId = :customerId ORDER BY t.nome")
    List<TagEntity> findTagsByCustomerId(@Param("customerId") Long customerId);
}
