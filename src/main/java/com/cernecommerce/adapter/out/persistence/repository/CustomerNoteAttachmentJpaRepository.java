package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerNoteAttachmentJpaRepository extends JpaRepository<CustomerNoteAttachmentEntity, Long> {

    List<CustomerNoteAttachmentEntity> findByNoteIdInOrderByIdAsc(Collection<Long> noteIds);
}
