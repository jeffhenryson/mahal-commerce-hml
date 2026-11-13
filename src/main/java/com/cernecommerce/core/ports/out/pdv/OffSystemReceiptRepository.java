package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port de saída do "valor recebido por fora" de um caixa. Os apagados nunca voltam nas leituras. */
public interface OffSystemReceiptRepository {

    OffSystemReceipt save(OffSystemReceipt receipt);

    /** Não apagados da sessão, mais recentes primeiro. */
    List<OffSystemReceipt> findBySessionId(Long sessionId);

    /** O lançamento, se existir, for da sessão e não estiver apagado. */
    Optional<OffSystemReceipt> findActive(Long sessionId, Long receiptId);

    /** Exclusão lógica. */
    void markDeleted(Long receiptId, String deletedBy, Instant deletedAt);
}
