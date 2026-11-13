package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.exception.pdv.OffSystemReceiptNotFoundException;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;
import com.cernecommerce.core.ports.in.OffSystemReceiptUseCase;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.OffSystemReceiptRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * "Valor recebido por fora" de um caixa. Fica fora do {@code PdvService} porque não toca na gaveta:
 * nada daqui entra no esperado nem no total recebido; é só para a conferência por operadora.
 */
public class OffSystemReceiptService implements OffSystemReceiptUseCase {

    private final CashRegisterRepository cashRegisterRepository;
    private final OffSystemReceiptRepository receiptRepository;

    public OffSystemReceiptService(CashRegisterRepository cashRegisterRepository,
            OffSystemReceiptRepository receiptRepository) {
        this.cashRegisterRepository = cashRegisterRepository;
        this.receiptRepository = receiptRepository;
    }

    @Override
    @Transactional
    public OffSystemReceipt register(Long sessionId, BigDecimal amount, PaymentMethod method,
            PaymentProvider provider, String note, String username, boolean canAnySession) {
        // Valida o corpo antes de travar: corpo inválido é 400 mesmo sem acesso à sessão.
        OffSystemReceipt receipt = OffSystemReceipt.register(sessionId, amount, method, provider, note, username);
        requireWritable(sessionId, username, canAnySession);
        return receiptRepository.save(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OffSystemReceipt> list(Long sessionId) {
        requireSession(sessionId);
        return receiptRepository.findBySessionId(sessionId);
    }

    @Override
    @Transactional
    public OffSystemReceipt delete(Long sessionId, Long receiptId, String username, boolean canAnySession) {
        requireWritable(sessionId, username, canAnySession);
        OffSystemReceipt receipt = receiptRepository.findActive(sessionId, receiptId)
                .orElseThrow(() -> new OffSystemReceiptNotFoundException(sessionId, receiptId));
        receiptRepository.markDeleted(receipt.id(), username, Instant.now());
        return receipt;
    }

    /** Aberto e do operador; com {@code canAnySession}, qualquer caixa, aberto ou fechado. */
    private void requireWritable(Long sessionId, String username, boolean canAnySession) {
        // PDV-C035 — mesma trava compartilhada das outras escritas na sessão: o fechamento espera.
        cashRegisterRepository.lockForShare(sessionId);
        CashRegisterSession session = requireSession(sessionId);
        if (canAnySession) {
            return;
        }
        if (!session.isOpen()) {
            throw new CashRegisterSessionClosedException(sessionId);
        }
        if (!session.belongsTo(username)) {
            throw new CashRegisterSessionNotOwnedException(sessionId, username);
        }
    }

    private CashRegisterSession requireSession(Long sessionId) {
        return cashRegisterRepository.findById(sessionId)
                .orElseThrow(() -> new CashRegisterSessionNotFoundException(sessionId));
    }
}
