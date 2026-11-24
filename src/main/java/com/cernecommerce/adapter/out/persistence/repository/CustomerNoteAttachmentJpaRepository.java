package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CustomerNoteAttachmentJpaRepository extends JpaRepository<CustomerNoteAttachmentEntity, Long> {

    List<CustomerNoteAttachmentEntity> findByNoteIdInOrderByIdAsc(Collection<Long> noteIds);

    /** V206 — chaves no storage dos anexos de todas as notas do cliente. */
    @org.springframework.data.jpa.repository.Query("SELECT a.storageKey FROM CustomerNoteAttachmentEntity a, "
            + "CustomerNoteEntity n WHERE a.noteId = n.id AND n.customerId = :customerId")
    List<String> findStorageKeysByCustomerId(
            @org.springframework.data.repository.query.Param("customerId") Long customerId);

    /** V206 — apaga os anexos de todas as notas do cliente. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("DELETE FROM CustomerNoteAttachmentEntity a WHERE a.noteId IN "
            + "(SELECT n.id FROM CustomerNoteEntity n WHERE n.customerId = :customerId)")
    int deleteAllByCustomerId(@org.springframework.data.repository.query.Param("customerId") Long customerId);
}
