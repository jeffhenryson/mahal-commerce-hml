package com.cernecommerce.core.ports.out.crm;

import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;

import java.util.List;

/**
 * Entrega ocorrências de automação fora da thread da requisição. A entrega faz chamada HTTP (webhook,
 * n8n, WhatsApp) e não pode segurar a resposta ao operador. Quem chama já está depois do commit
 * ({@code AfterCommitExecutor}). A idempotência continua no {@code key} de cada ocorrência.
 */
public interface AutomationDispatchQueue {

    void enqueue(List<AutomationOccurrence> occurrences);
}
