package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Valor recebido por fora do sistema; só para a conferência por operadora, fora do esperado")
public record OffSystemReceiptResponseDTO(Long id, BigDecimal amount, PaymentMethod method, PaymentProvider provider,
        String note, Instant createdAt, String username) {

    public static OffSystemReceiptResponseDTO from(OffSystemReceipt receipt) {
        return new OffSystemReceiptResponseDTO(receipt.id(), receipt.amount(), receipt.method(), receipt.provider(),
                receipt.note(), receipt.createdAt(), receipt.username());
    }
}
