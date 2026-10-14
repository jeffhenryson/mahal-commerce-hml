package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivity;
import com.cernecommerce.core.domain.model.pdv.CashSessionActivityType;
import com.cernecommerce.core.ports.in.CashSessionActivityUseCase;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.CashSessionActivityRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PDV-F047 — o diário do turno. Fica fora do {@code PdvService} porque não toca em dinheiro, estoque
 * nem pedido: só precisa saber se a sessão existe, está aberta e é de quem anota.
 */
public class CashSessionActivityService implements CashSessionActivityUseCase {

    private final CashRegisterRepository cashRegisterRepository;
    private final CashSessionActivityRepository activityRepository;

    public CashSessionActivityService(CashRegisterRepository cashRegisterRepository,
            CashSessionActivityRepository activityRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    @Transactional
    public CashSessionActivity register(Long sessionId, CashSessionActivityType type, String note, String username,
            boolean canAnySession) {
        // Valida o corpo antes de travar: OUTRO sem nota é 400 mesmo com a sessão fechada.
        CashSessionActivity activity = CashSessionActivity.register(sessionId, type, note, username);
        // PDV-C035 — mesma trava compartilhada das outras escritas na sessão: o fechamento espera.
        cashRegisterRepository.lockForShare(sessionId);
        CashRegisterSession session = requireSession(sessionId);
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        if (!canAnySession && !session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
        return activityRepository.save(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CashSessionActivity> list(Long sessionId) {
        requireSession(sessionId);
        return activityRepository.findBySessionId(sessionId);
    }

    private CashRegisterSession requireSession(Long sessionId) {
        return cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
    }
}
