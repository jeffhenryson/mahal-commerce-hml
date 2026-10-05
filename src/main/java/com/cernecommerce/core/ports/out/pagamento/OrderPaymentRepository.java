package com.cernecommerce.core.ports.out.pagamento;

import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Collection;
import java.util.List;

/**
 * Port de saída para o ledger de pagamentos de pedido (PDV-F006).
 *
 * <p>Append-only por desenho: não há {@code update}. Corrigir um pagamento errado é uma linha nova
 * em sentido contrário (estorno, PDV-F007), não uma edição — a mesma regra do resto do módulo.
 * <b>Exceção documentada:</b> {@link OrderPayment#confirmCaptured} (ECM-F004) atualiza a própria
 * linha em vez de criar uma nova — {@code uk_order_payment_gateway_ref} é único, e a confirmação
 * de um pagamento de gateway não tem uma segunda referência para usar como o estorno tem. Não
 * "conserte" isso de volta para uma linha nova sem reler o javadoc de {@code confirmCaptured}.</p>
 */
public interface OrderPaymentRepository {

    /** PDV-F026 — métodos {@code CAPTURED} distintos de cada pedido; pedido sem pagamento não aparece. */
    Map<Long, List<PaymentMethod>> findCapturedMethodsByOrderIds(Collection<Long> orderIds);


    OrderPayment save(OrderPayment payment);

    /** Pagamentos de um pedido, na ordem em que foram lançados. */
    List<OrderPayment> findByOrderId(Long orderId);

    /**
     * Soma dos pagamentos {@code CAPTURED} de um método, entre os pedidos liquidados por uma
     * sessão de caixa — é o que dá o total "recebido em dinheiro", "recebido em débito" etc. no
     * fechamento. Zero quando não houve nenhum, nunca {@code null}.
     */
    BigDecimal sumCapturedAmountBySessionIdAndMethod(Long sessionId, PaymentMethod method);

    /**
     * Soma dos pagamentos {@code REFUNDED} de um método, entre os pedidos da sessão — o que
     * <b>saiu</b> da gaveta por estorno (PDV-C018). Zero quando não houve nenhum, nunca
     * {@code null}.
     *
     * <p>Precisa existir como consulta própria justamente por causa da regra append-only descrita
     * acima: o estorno é uma linha nova e a {@code CAPTURED} original permanece, então
     * {@link #sumCapturedAmountBySessionIdAndMethod} sozinha descreve tudo que entrou e nada do
     * que voltou.</p>
     */
    BigDecimal sumRefundedAmountBySessionIdAndMethod(Long sessionId, PaymentMethod method);

    /**
     * PDV-F038 — soma das linhas {@code MARCADO} ({@code ON_ACCOUNT}, CRM-F010) dos pedidos da
     * sessão: o que foi vendido para o cliente pagar outro dia. Fica fora do total recebido. Zero
     * quando não houve nenhuma, nunca {@code null}.
     */
    BigDecimal sumOnAccountAmountBySessionId(Long sessionId);

    /**
     * Pagamento pelo identificador do gateway (ECM-F004) — usado pelo webhook para checar
     * idempotência antes de processar uma notificação.
     */
    java.util.Optional<OrderPayment> findByGatewayRef(String gatewayRef);
}
