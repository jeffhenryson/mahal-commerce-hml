package com.cernecommerce.core.domain.model.assistente;

/** Quem escreveu a mensagem. O valor em minúsculas é o que o front recebe. */
public enum PapelMensagem {
    USUARIO, ASSISTENTE;

    public String api() {
        return name().toLowerCase();
    }
}
