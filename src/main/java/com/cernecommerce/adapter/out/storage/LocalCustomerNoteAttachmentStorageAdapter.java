package com.cernecommerce.adapter.out.storage;

import com.cernecommerce.core.ports.out.storage.CustomerNoteAttachmentStoragePort;

import java.nio.file.Path;

/** Anexos de nota do CRM em disco local, fora de qualquer pasta servida estaticamente. */
public class LocalCustomerNoteAttachmentStorageAdapter extends LocalFileStorageAdapter
        implements CustomerNoteAttachmentStoragePort {

    public LocalCustomerNoteAttachmentStorageAdapter(Path storageDir) {
        super(storageDir, "crm-note-attachment");
    }
}
