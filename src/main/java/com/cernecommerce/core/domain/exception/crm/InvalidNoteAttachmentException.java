package com.cernecommerce.core.domain.exception.crm;

/** Anexo de nota recusado: tipo não aceito, vazio, grande demais ou anexos demais. */
public class InvalidNoteAttachmentException extends RuntimeException {
    public InvalidNoteAttachmentException(String message) {
        super(message);
    }
}
