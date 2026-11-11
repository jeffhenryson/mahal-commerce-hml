package com.cernecommerce.core.ports.out.assistente;

import com.cernecommerce.core.domain.model.assistente.FeedbackRegistro;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.UsoTokens;

import java.time.Instant;
import java.util.List;

/** Tokens gastos por resposta do assistente e as leituras do relatório de uso e de feedback. */
public interface AssistenteUsoPort {

    /** Linhas de uso agrupadas por usuário e provedor. */
    record UsoAgrupado(String username, String provedor, long respostas, UsoTokens uso) { }

    void registrar(String mensagemId, UsoTokens uso);

    /** Soma de entrada + saída (sem cache) das respostas criadas em {@code [de, ate)}. */
    long tokensNoLimite(Instant de, Instant ate);

    List<UsoAgrupado> usoPorUsuario(Instant de, Instant ate);

    /** Mais recentes primeiro. */
    List<FeedbackRegistro> feedbacks(NotaFeedback nota, int limite);
}
