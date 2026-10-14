package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;

import java.util.List;

/** Port de saída das atividades da sessão de caixa (PDV-F047). Append-only, como o de movimentos. */
public interface CashSessionActivityRepository {

    CashSessionActivity save(CashSessionActivity activity);

    /** Atividades da sessão, as mais recentes primeiro. Um turno tem dezenas, não milhares: sem página. */
    List<CashSessionActivity> findBySessionId(Long sessionId);
}
