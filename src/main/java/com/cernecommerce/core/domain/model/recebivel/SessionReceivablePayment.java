package com.cernecommerce.core.domain.model.recebivel;

import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Uma quitação de marcado recebida num caixa, do jeito que o extrato do caixa mostra.
 *
 * <p>{@code amount} é o valor <b>entregue</b>: na linha que carrega o troco do lote (a última em
 * DINHEIRO), é o abatido mais o troco, e {@code changeAmount} é o troco. Nas outras linhas,
 * {@code changeAmount} é zero. Assim {@code Σamount − ΣchangeAmount} é o abatido, o mesmo número de
 * {@code receivableReceived} no resumo do caixa (fora os estornados).</p>
 */
public record SessionReceivablePayment(Long id, Long receivableId, Long batchId, Long customerId,
        String customerName, PaymentMethod method, PaymentChannel channel, PaymentProvider provider,
        BigDecimal amount, BigDecimal changeAmount, Instant createdAt, String username, PaymentStatus status) {

    /**
     * Monta o extrato a partir das linhas gravadas. Um lote com troco e nenhuma linha em DINHEIRO
     * (todo o dinheiro voltou como troco) não ganha linha: o efeito na gaveta é zero.
     *
     * @return mais recentes primeiro
     */
    public static List<SessionReceivablePayment> assemble(List<ReceivablePayment> payments,
            Collection<ReceivablePaymentBatch> batches, Set<Long> reversedBatchIds,
            Map<Long, String> customerNames) {
        Map<Long, BigDecimal> changeByBatch = batches.stream().collect(Collectors.toMap(
                ReceivablePaymentBatch::id,
                b -> b.changeAmount() == null ? BigDecimal.ZERO : b.changeAmount(), (a, b) -> a));
        // Última linha em DINHEIRO de cada lote: é ela que carrega o troco.
        Map<Long, Long> changeCarrierByBatch = payments.stream()
                .filter(p -> p.method() == PaymentMethod.DINHEIRO)
                .collect(Collectors.toMap(ReceivablePayment::batchId, ReceivablePayment::id, Long::max));

        List<SessionReceivablePayment> result = new ArrayList<>(payments.size());
        for (ReceivablePayment p : payments) {
            BigDecimal change = p.id().equals(changeCarrierByBatch.get(p.batchId()))
                    ? changeByBatch.getOrDefault(p.batchId(), BigDecimal.ZERO)
                    : BigDecimal.ZERO;
            PaymentStatus status = reversedBatchIds.contains(p.batchId())
                    ? PaymentStatus.REFUNDED : PaymentStatus.CAPTURED;
            result.add(new SessionReceivablePayment(p.id(), p.receivableId(), p.batchId(), p.customerId(),
                    customerNames.get(p.customerId()), p.method(), p.channel(), p.provider(),
                    p.amount().add(change), change, p.receivedAt(), p.receivedBy(), status));
        }
        result.sort(Comparator.comparing(SessionReceivablePayment::createdAt)
                .thenComparing(SessionReceivablePayment::id).reversed());
        return result;
    }
}
