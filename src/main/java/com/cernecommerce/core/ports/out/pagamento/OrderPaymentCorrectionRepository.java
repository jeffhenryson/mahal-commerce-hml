package com.cernecommerce.core.ports.out.pagamento;

import com.cernecommerce.core.domain.model.pagamento.CashSessionAdjustment;
import com.cernecommerce.core.domain.model.pagamento.OrderPaymentCorrection;

import java.util.List;

/** Correções de forma de pagamento e os ajustes que elas deixam em caixa fechado (PDV-F030). */
public interface OrderPaymentCorrectionRepository {

    OrderPaymentCorrection save(OrderPaymentCorrection correction);

    /** Da mais antiga para a mais recente. */
    List<OrderPaymentCorrection> findByOrderId(Long orderId);

    CashSessionAdjustment saveAdjustment(CashSessionAdjustment adjustment);
}
