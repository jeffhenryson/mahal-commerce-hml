package com.cernecommerce.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteAttachmentEntity;
import com.cernecommerce.core.domain.model.crm.CustomerNoteAttachment;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.adapter.out.persistence.entity.CustomerNoteEntity;
import com.cernecommerce.core.domain.model.crm.CustomerNote;
import com.cernecommerce.core.ports.out.crm.CustomerNoteRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class CustomerNoteRepositoryImpl implements CustomerNoteRepository {

    private final CustomerNoteJpaRepository customerNoteJpaRepository;
    private final CustomerNoteAttachmentJpaRepository attachmentJpaRepository;

    public CustomerNoteRepositoryImpl(CustomerNoteJpaRepository customerNoteJpaRepository,
            CustomerNoteAttachmentJpaRepository attachmentJpaRepository) {
        this.customerNoteJpaRepository = customerNoteJpaRepository;
        this.attachmentJpaRepository = attachmentJpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<CustomerNote> findByCustomerId(Long customerId, int page, int size) {
        Page<CustomerNoteEntity> result = customerNoteJpaRepository.findByCustomerIdOrderByCriadoEmDescIdDesc(
                customerId, PageRequest.of(page, size));
        List<Long> ids = result.getContent().stream().map(CustomerNoteEntity::getId).toList();
        // Anexos da página inteira numa consulta só.
        Map<Long, List<CustomerNoteAttachment>> byNote = new HashMap<>();
        if (!ids.isEmpty()) {
            for (CustomerNoteAttachmentEntity a : attachmentJpaRepository.findByNoteIdInOrderByIdAsc(ids)) {
                byNote.computeIfAbsent(a.getNoteId(), k -> new ArrayList<>()).add(toDomain(a));
            }
        }
        List<CustomerNote> content = result.getContent().stream()
                .map(e -> toDomain(e).withAnexos(byNote.getOrDefault(e.getId(), List.of())))
                .toList();
        return new PageResult<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    public CustomerNoteAttachment saveAttachment(CustomerNoteAttachment attachment) {
        CustomerNoteAttachmentEntity e = new CustomerNoteAttachmentEntity();
        e.setNoteId(attachment.noteId());
        e.setNomeArquivo(attachment.nomeArquivo());
        e.setContentType(attachment.contentType());
        e.setTamanho(attachment.tamanho());
        e.setStorageKey(attachment.storageKey());
        e.setCriadoEm(attachment.criadoEm());
        return toDomain(attachmentJpaRepository.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerNoteAttachment> findAttachment(Long attachmentId) {
        return attachmentJpaRepository.findById(attachmentId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerNote> findById(Long noteId) {
        return customerNoteJpaRepository.findById(noteId).map(this::toDomain);
    }

    private CustomerNoteAttachment toDomain(CustomerNoteAttachmentEntity a) {
        return new CustomerNoteAttachment(a.getId(), a.getNoteId(), a.getNomeArquivo(), a.getContentType(),
                a.getTamanho(), a.getStorageKey(), a.getCriadoEm());
    }

    @Override
    public CustomerNote save(CustomerNote note) {
        CustomerNoteEntity entity = new CustomerNoteEntity();
        entity.setId(note.id());
        entity.setCustomerId(note.customerId());
        entity.setAutor(note.autor());
        entity.setTexto(note.texto());
        entity.setCriadoEm(note.criadoEm());
        CustomerNoteEntity saved = customerNoteJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerNote> findByCustomerId(Long customerId) {
        return customerNoteJpaRepository.findByCustomerIdOrderByCriadoEmDesc(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    private CustomerNote toDomain(CustomerNoteEntity e) {
        return CustomerNote.of(e.getId(), e.getCustomerId(), e.getAutor(), e.getTexto(), e.getCriadoEm());
    }
}
