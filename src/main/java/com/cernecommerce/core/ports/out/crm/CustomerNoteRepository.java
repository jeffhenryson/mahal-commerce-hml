package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.CustomerNote;

import java.util.List;

/**
 * Port de saída para persistência de notas de clientes do CRM.
 */
public interface CustomerNoteRepository {

    CustomerNote save(CustomerNote note);

    /** Lista as notas de um cliente, mais recentes primeiro. */
    List<CustomerNote> findByCustomerId(Long customerId);

    /** V183 — página de notas, mais recentes primeiro, já com os anexos. */
    com.cernecommerce.core.domain.model.PageResult<CustomerNote> findByCustomerId(Long customerId, int page, int size);

    com.cernecommerce.core.domain.model.crm.CustomerNoteAttachment saveAttachment(
            com.cernecommerce.core.domain.model.crm.CustomerNoteAttachment attachment);

    java.util.Optional<com.cernecommerce.core.domain.model.crm.CustomerNoteAttachment> findAttachment(Long attachmentId);

    java.util.Optional<CustomerNote> findById(Long noteId);

    /** V206 — chaves no storage de todos os anexos das notas do cliente. */
    List<String> findAttachmentKeysByCustomerId(Long customerId);

    /** V206 — apaga as notas do cliente e os anexos delas (só as linhas; os arquivos são do serviço). */
    void deleteAllByCustomerId(Long customerId);
}
