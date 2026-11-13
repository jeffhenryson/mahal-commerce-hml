package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port de entrada do "valor recebido por fora" de um caixa.
 *
 * <p>Escrita (lançar e apagar): com o caixa aberto, o operador dono do caixa; em qualquer caixa,
 * aberto ou fechado, quem tem {@code PDV_SESSION_CLOSE_ANY} ({@code canAnySession}).</p>
 */
public interface OffSystemReceiptUseCase {

    /**
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException fechada, sem
     *         {@code canAnySession}
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException de outro
     *         operador, sem {@code canAnySession}
     * @throws IllegalArgumentException valor, forma ou observação inválidos
     */
    OffSystemReceipt register(Long sessionId, BigDecimal amount, PaymentMethod method, PaymentProvider provider,
            String note, String username, boolean canAnySession);

    /** Lançamentos não apagados da sessão, mais recentes primeiro. */
    List<OffSystemReceipt> list(Long sessionId);

    /**
     * Exclusão lógica, com a mesma regra de acesso de {@link #register}.
     *
     * @return o lançamento apagado
     * @throws com.cernecommerce.core.domain.exception.pdv.OffSystemReceiptNotFoundException se não for da
     *         sessão ou já estiver apagado
     */
    OffSystemReceipt delete(Long sessionId, Long receiptId, String username, boolean canAnySession);
}
