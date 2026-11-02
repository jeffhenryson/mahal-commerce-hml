package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;

/**
 * Anexo de uma nota do cliente (V183): documento ou imagem. O arquivo fica no storage privado; aqui
 * só a referência. Nunca tem URL pública — é servido pela API, com a permissão de leitura do CRM.
 */
public record CustomerNoteAttachment(Long id, Long noteId, String nomeArquivo, String contentType, long tamanho,
        String storageKey, Instant criadoEm) {

    public CustomerNoteAttachment {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            throw new IllegalArgumentException("nomeArquivo é obrigatório");
        }
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey é obrigatório");
        }
    }
}
