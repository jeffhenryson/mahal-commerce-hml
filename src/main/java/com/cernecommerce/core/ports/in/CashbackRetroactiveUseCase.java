package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactivePreview;

import java.math.BigDecimal;

/** Credita a taxa GLOBAL sobre os pedidos pagos que nunca geraram cashback. Pode ser repetido. */
public interface CashbackRetroactiveUseCase {

    /** Não grava nada e nunca recusa por regra de negócio: devolve {@code eligible = false} e o motivo. */
    CashbackRetroactivePreview preview(Long customerId);

    /** Recalcula tudo no servidor. {@code expectedCreditAmount} só confere que o admin viu o mesmo valor. */
    Result grant(Long customerId, BigDecimal expectedCreditAmount);

    record Result(int ordersCredited, BigDecimal creditedAmount, CashbackBalance balance) {
    }
}
