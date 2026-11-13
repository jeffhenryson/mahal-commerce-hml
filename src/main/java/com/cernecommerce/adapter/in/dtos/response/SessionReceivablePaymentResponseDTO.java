package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.recebivel.SessionReceivablePayment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/** Quitação de marcado recebida num caixa: uma linha do extrato e da conferência por maquininha. */
@Schema(description = "Quitação de marcado recebida no caixa. amount é o entregue; o troco está em changeAmount.")
public record SessionReceivablePaymentResponseDTO(Long id, Long receivableId, Long batchId, String customerName,
        PaymentMethod method, PaymentChannel channel, PaymentProvider provider, BigDecimal amount,
        BigDecimal changeAmount, Instant createdAt, String username, PaymentStatus status) {

    public static SessionReceivablePaymentResponseDTO from(SessionReceivablePayment p) {
        return new SessionReceivablePaymentResponseDTO(p.id(), p.receivableId(), p.batchId(), p.customerName(),
                p.method(), p.channel(), p.provider(), p.amount(), p.changeAmount(), p.createdAt(), p.username(),
                p.status());
    }
}
