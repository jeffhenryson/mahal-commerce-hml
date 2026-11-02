package com.cernecommerce.core.domain.exception.crm;

/** Anexo inexistente, ou de outra nota/cliente. */
public class CustomerNoteAttachmentNotFoundException extends RuntimeException {
    public CustomerNoteAttachmentNotFoundException(Long attachmentId) {
        super("Anexo " + attachmentId + " não encontrado");
    }
}
