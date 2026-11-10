package com.cernecommerce.core.domain.model.config;

import java.util.Locale;
import java.util.regex.Pattern;

/** Provedores de IA que o assistente do admin sabe usar. Cada um tem chave e modelo próprios. */
public enum ProvedorIa {

    ANTHROPIC("Claude (Anthropic)", "claude-[a-z0-9.-]{1,80}"),
    OPENAI("ChatGPT (OpenAI)", "(gpt|o\\d)[a-z0-9.-]{0,80}"),
    GEMINI("Gemini (Google)", "gemini-[a-z0-9.-]{1,80}");

    private final String rotulo;
    private final Pattern modelo;

    ProvedorIa(String rotulo, String modelo) {
        this.rotulo = rotulo;
        this.modelo = Pattern.compile(modelo);
    }

    public String rotulo() {
        return rotulo;
    }

    /** Segmento das chaves em {@code system_config}: {@code integration.assistant.<chave>.*}. */
    public String chave() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean modeloValido(String id) {
        return id != null && modelo.matcher(id).matches();
    }

    /** {@code null} ou em branco = nenhum; valor desconhecido lança {@link IllegalArgumentException}. */
    public static ProvedorIa de(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valueOf(valor.strip().toUpperCase(Locale.ROOT));
    }
}
