package com.cernecommerce.core.domain.model.pdv;

import java.time.Instant;

/**
 * Atividade anotada na sessão de caixa (PDV-F047): varrer a loja, trocar dinheiro, repor mercadoria.
 * Não mexe em valor nenhum — é o diário do turno, mostrado no painel da sessão. Append-only como o
 * {@link CashMovement}: corrigir é anotar de novo, não editar.
 */
public record CashSessionActivity(Long id, Long sessionId, CashSessionActivityType type, String note,
        String username, Instant createdAt) {

    public static final int NOTE_MAX_LENGTH = 280;

    public CashSessionActivity {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (type == null) {
            throw new IllegalArgumentException("type é obrigatório");
        }
        note = note == null || note.isBlank() ? null : note.trim();
        if (type == CashSessionActivityType.OUTRO && note == null) {
            throw new IllegalArgumentException("note é obrigatória quando type = OUTRO");
        }
        if (note != null && note.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("note tem no máximo " + NOTE_MAX_LENGTH + " caracteres");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username é obrigatório");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt é obrigatório");
        }
    }

    /** Registra uma atividade nova, no instante atual. */
    public static CashSessionActivity register(Long sessionId, CashSessionActivityType type, String note,
            String username) {
        return new CashSessionActivity(null, sessionId, type, note, username, Instant.now());
    }

    /** Reconstitui a partir de persistência. */
    public static CashSessionActivity of(Long id, Long sessionId, CashSessionActivityType type, String note,
            String username, Instant createdAt) {
        return new CashSessionActivity(id, sessionId, type, note, username, createdAt);
    }
}
