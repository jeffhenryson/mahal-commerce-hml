package com.cernecommerce.infra.audit;

import java.util.function.Supplier;

/**
 * Origem das ações auditadas na thread atual. O assistente de IA marca {@code ASSISTENTE} em volta
 * de cada ferramenta e de cada confirmação; o {@link AuditEventListener}, que roda na mesma thread
 * do controller, copia a marca para o {@code details} antes de gravar.
 *
 * <p>Também guarda IP e User-Agent do request original: o turno do assistente roda num executor
 * próprio, fora da thread do request, onde o listener não acharia os dois.</p>
 */
public final class AuditOrigemContext {

    public static final String ASSISTENTE = "ASSISTENTE";

    private static final ThreadLocal<Origem> ATUAL = new ThreadLocal<>();

    private AuditOrigemContext() { }

    public record Origem(String nome, String ip, String userAgent) { }

    public static Origem atual() {
        return ATUAL.get();
    }

    public static <T> T executar(Origem origem, Supplier<T> acao) {
        Origem anterior = ATUAL.get();
        ATUAL.set(origem);
        try {
            return acao.get();
        } finally {
            if (anterior == null) {
                ATUAL.remove();
            } else {
                ATUAL.set(anterior);
            }
        }
    }
}
