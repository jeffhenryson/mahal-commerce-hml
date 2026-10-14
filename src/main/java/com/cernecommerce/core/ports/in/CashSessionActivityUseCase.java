package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivityType;

import java.util.List;

/** Port de entrada das atividades da sessão de caixa (PDV-F047). */
public interface CashSessionActivityUseCase {

    /**
     * Anota uma atividade. Exige a sessão aberta e do próprio operador — salvo {@code canAnySession}
     * (quem tem {@code PDV_SESSION_CLOSE_ANY}, o gerente).
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException se estiver fechada
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException se for de outro
     *         operador e {@code canAnySession} for falso
     * @throws IllegalArgumentException {@code OUTRO} sem {@code note}, ou {@code note} acima de 280 caracteres
     */
    CashSessionActivity register(Long sessionId, CashSessionActivityType type, String note, String username,
            boolean canAnySession);

    /** Atividades da sessão, as mais recentes primeiro. */
    List<CashSessionActivity> list(Long sessionId);
}
