package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.AssistenteMensagemEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssistenteMensagemJpaRepository extends JpaRepository<AssistenteMensagemEntity, String> {

    List<AssistenteMensagemEntity> findByConversaIdOrderByCriadaEmAscIdAsc(String conversaId);

    List<AssistenteMensagemEntity> findByConversaIdOrderByCriadaEmDescIdDesc(String conversaId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AssistenteMensagemEntity m WHERE m.conversaId IN :ids")
    int deleteByConversaIds(@Param("ids") List<String> ids);
}
