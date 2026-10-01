package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.OrderPaymentCorrection;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pedido.OrderFilter;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;

import java.time.Instant;
import java.util.Map;
import java.util.Collection;
import java.time.LocalDate;
import java.util.List;

/**
 * Port de entrada da visão de <b>pedidos do administrador</b> — atravessa canais.
 *
 * <p>Fica fora de {@code PdvUseCase} de propósito: o PDV enxerga a operação de um caixa, enquanto
 * esta superfície enxerga o pedido independentemente de ter nascido no balcão ou no site. Misturar
 * as duas faria o PDV crescer para caber o marketplace.</p>
 */
public interface OrderUseCase {

    /** Listagem filtrada, do mais recente para o mais antigo. Filtro {@code null} é ignorado. */
    PageResult<Order> listOrders(SalesChannel channel, OrderStatus status, Long customerId,
            Instant from, Instant to, int page, int size);

    /** Listagem com os filtros de caixa, comanda e número do pedido (PDV-F026). */
    PageResult<Order> listOrders(OrderFilter filter, int page, int size);

    /**
     * PDV-F026 — métodos {@code CAPTURED} de cada pedido informado, numa consulta só: a listagem
     * mostra como cada pedido foi pago sem abrir um recibo por linha.
     */
    Map<Long, List<PaymentMethod>> getCapturedPaymentMethods(Collection<Long> orderIds);

    /**
     * Busca um pedido pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se não existir
     */
    Order getOrder(Long orderId);

    /**
     * Pagamentos do pedido, de qualquer canal (GET /orders/{id}/receipt). Simétrico a
     * {@code PdvUseCase.getOrderPayments}, mas nesta superfície cross-canal — o recibo de um
     * pedido de marketplace não deveria depender de {@code PdvUseCase}, que enxerga só a operação
     * de um caixa.
     */
    List<OrderPayment> getOrderPayments(Long orderId);

    /**
     * Avança o pedido na esteira de fulfillment.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se a transição não for permitida pela máquina de estados
     */
    Order changeStatus(Long orderId, OrderStatus newStatus, String username);

    /**
     * Edita a entrega de um pedido depois da venda (PDV-F022) — códigos da 99, rastreio dos
     * Correios, entregador, correção de endereço. Campo {@code null} mantém o atual.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se não existir
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderHasNoDeliveryException
     *         se o pedido não foi vendido com entrega ou retirada
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderDeliveryNotEditableException
     *         se o pedido estiver cancelado ou reembolsado
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException
     *         se tentar mudar a taxa ou o tipo, ou deixar o endereço incompleto
     */
    Order updateDelivery(Long orderId, OrderDelivery.Patch patch, String username);

    /**
     * Cancela um pedido ANTES de qualquer pagamento confirmado e <b>libera a reserva de
     * estoque</b> — nunca houve baixa real para devolver, só reserva (ou nem isso, no balcão, que
     * nunca reserva).
     *
     * <p>Cancelar e reembolsar são eventos diferentes (PDV-F007): contá-los juntos esconderia
     * quanto dinheiro de fato voltou ao cliente. Pedido com pagamento já confirmado usa
     * {@link #refundOrder}.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido já tiver pagamento confirmado (use {@link #refundOrder}) ou já estiver
     *         {@code CANCELADO}
     */
    Order cancelOrder(Long orderId, String reason, String username);

    /**
     * Reembolsa um pedido DEPOIS de pagamento confirmado, tudo na mesma transação: devolve a
     * mercadoria ao estoque (EST-F014 chegando pela porta do pedido), estorna cada pagamento
     * {@code CAPTURED} com uma linha {@code REFUNDED} do mesmo método e valor, e reverte no ledger
     * de cashback todo ganho {@code EARNED} do pedido.
     *
     * <p>Reembolsar um pedido já entregue é uma <b>devolução</b> — e devolução é entrada de
     * estoque legítima. O motivo do {@code StockMovement} carrega o número do pedido para a
     * trilha ser reconstruível.</p>
     *
     * <p>Equivale a {@link #refundOrder(Long, String, String, List)} sem nenhum item lote-rastreado
     * — se o pedido tiver algum, o reembolso falha com
     * {@code MissingLotInfoException} (o mesmo erro de {@code adjustStock}), porque não tem como
     * o sistema adivinhar em qual lote a mercadoria devolvida volta a ficar.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido ainda não tiver pagamento confirmado (use {@link #cancelOrder}) ou já
     *         estiver {@code CANCELADO}/{@code REEMBOLSADO}
     */
    Order refundOrder(Long orderId, String reason, String username);

    /** O lote em que a mercadoria devolvida de {@code sku} volta a ficar (EST-F008). */
    record RefundItemLot(String sku, String lotCode, LocalDate expiryDate) {
    }

    /**
     * Mesmo que {@link #refundOrder(Long, String, String)}, com o lote de retorno explícito por
     * SKU para item lote-rastreado — o casamento é por {@code sku}: se o pedido tiver duas linhas
     * do mesmo SKU, as duas recebem a mesma info de lote. SKU sem entrada em {@code itemLots} usa
     * o overload sem lote de {@code adjustStock}, o que só é válido se o SKU não for
     * lote-rastreado.
     */
    Order refundOrder(Long orderId, String reason, String username, List<RefundItemLot> itemLots);

    /**
     * Corrige a forma de pagamento de um pedido sem perder o registro do erro (PDV-F027).
     *
     * <p>As linhas {@code CAPTURED} vigentes passam a {@code CORRECTED}; as informadas nascem
     * {@code CAPTURED}. A soma tem que ser exatamente {@code totalPayable} — sem troco, que já foi
     * devolvido na venda; se havia troco, {@code changeAmount} vai a zero. Com o caixa do pedido já
     * fechado, exige {@code canCorrectClosed} e grava a divergência por método no caixa fechado.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pagamento.CorrectionReasonRequiredException
     * @throws com.cernecommerce.core.domain.exception.pagamento.OrderNotCorrectableException
     * @throws com.cernecommerce.core.domain.exception.pagamento.GatewayPaymentNotCorrectableException
     * @throws com.cernecommerce.core.domain.exception.pagamento.PaymentTotalMismatchException
     * @throws com.cernecommerce.core.domain.exception.pagamento.CashSessionClosedForCorrectionException
     */
    PaymentCorrectionResult correctPayments(Long orderId, List<PdvUseCase.PaymentCommand> payments,
            String reason, String username, boolean canCorrectClosed);

    /** Correções do pedido, da mais antiga para a mais recente; {@code []} se nunca houve. */
    List<PaymentCorrectionEntry> getPaymentHistory(Long orderId);

    /** O pedido depois da correção, com o antes e o depois — o controller audita os dois. */
    record PaymentCorrectionResult(Order order, OrderPaymentCorrection correction,
            List<OrderPayment> before, List<OrderPayment> after) {
    }

    /** Uma correção com as linhas que aposentou ({@code before}) e as que lançou ({@code after}). */
    record PaymentCorrectionEntry(OrderPaymentCorrection correction, List<OrderPayment> before,
            List<OrderPayment> after) {
    }
}
