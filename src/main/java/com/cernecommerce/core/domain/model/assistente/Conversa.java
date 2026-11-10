package com.cernecommerce.core.domain.model.assistente;

import java.time.Instant;

/** Conversa do assistente de IA. Só o dono enxerga; outro usuário recebe 404. */
public record Conversa(String id, String username, String titulo, Instant criadaEm, Instant atualizadaEm) {

    public boolean pertenceA(String user) {
        return username.equals(user);
    }
}
