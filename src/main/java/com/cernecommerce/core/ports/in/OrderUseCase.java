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
     * Unidades de um SKU a estornar e o destino delas (PED-F004). Um SKU que aparece em mais de uma
     * linha do pedido é distribuído pelas linhas na ordem em que foram vendidas.
     */
    record RefundItemCommand(String sku, java.math.BigDecimal quantity,
            com.cernecommerce.core.domain.model.pedido.RefundDestination destination) {
    }

    /**
     * O estorno completo (PED-F004).
     *
     * @param restock  sem {@code items}: se a mercadoria volta ao estoque (o padrão de sempre) ou fica
     *                 com o cliente
     * @param items    vazio = estorna tudo o que ainda não foi estornado; preenchido = só essas unidades
     */
    record RefundCommand(String reason, boolean restock, List<RefundItemLot> itemLots,
            List<RefundItemCommand> items) {

        public RefundCommand {
            itemLots = itemLots == null ? List.of() : List.copyOf(itemLots);
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /**
     * Estorno total ou por item (PED-F004).
     *
     * <ul>
     *   <li>Sem {@code items}: o comportamento de {@link #refundOrder(Long, String, String, List)} sobre o
     *       que ainda não foi estornado, com {@code restock} decidindo o estoque.</li>
     *   <li>Com {@code items}: estorna só as unidades pedidas. O dinheiro é o líquido proporcional delas,
     *       rateado entre as formas de pagamento do pedido; {@code RESTOCK} dá ENTRADA no estoque,
     *       {@code DISCARD} e {@code CUSTOMER_KEEPS} não mexem no saldo. O pedido só vira
     *       {@code REEMBOLSADO} quando a última unidade sai — e esse último estorno devolve todo o
     *       restante pago, taxas incluídas.</li>
     * </ul>
     *
     * O estorno conta no caixa aberto de {@code username}; sem caixa aberto, no caixa do pedido.
     *
     * @param ownSessionOnly quem estorna só pode estornar pedido da própria sessão de caixa aberta
     * @throws com.cernecommerce.core.domain.exception.pedido.RefundQuantityExceededException se pedir
     *         mais do que resta de um SKU
     * @throws com.cernecommerce.core.domain.exception.pedido.RefundNotOwnSessionException se
     *         {@code ownSessionOnly} e o pedido não for da sessão aberta de {@code username}
     */
    Order refundOrder(Long orderId, RefundCommand command, String username, boolean ownSessionOnly);

    /** PED-F004 — unidades já estornadas por item do pedido; item sem estorno fica fora do mapa. */
    Map<Long, java.math.BigDecimal> getRefundedQuantities(Long orderId);

    /**
     * A troca de um item (PED-F006).
     *
     * @param destination      {@code RESTOCK} ou {@code DISCARD} para as unidades devolvidas
     * @param settlementMethod forma da diferença ({@code PIX}, {@code DINHEIRO}, {@code DEBITO} ou
     *                         {@code CREDITO}); só obrigatória quando há diferença
     */
    record ExchangeCommand(String sku, java.math.BigDecimal quantity,
            com.cernecommerce.core.domain.model.pedido.RefundDestination destination, String newSku,
            java.math.BigDecimal newQuantity, String reason,
            com.cernecommerce.core.domain.model.pagamento.PaymentMethod settlementMethod) {
    }

    /**
     * Troca unidades de um item do pedido por outro produto, no mesmo pedido (PED-F006). As unidades
     * devolvidas viram uma perna de estorno por item (RESTOCK dá ENTRADA no depósito do pedido;
     * DISCARD é perda, sem mexer no saldo), o novo SKU entra no pedido pelo preço efetivo atual, sem
     * desconto, com SAIDA no depósito do pedido, e só a diferença move dinheiro: {@code > 0} vira uma
     * linha CAPTURED no caixa aberto de {@code username}; {@code < 0}, uma linha REFUNDED (em dinheiro,
     * sai da gaveta dele).
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotExchangeableException pedido não
     *         pago, cancelado, totalmente reembolsado ou de mesa
     * @throws com.cernecommerce.core.domain.exception.pedido.ExchangeItemInvalidException SKU fora do
     *         pedido, quantidade acima do que resta ou linha que não é de catálogo
     * @throws com.cernecommerce.core.domain.exception.pedido.SettlementMethodRequiredException
     *         diferença sem forma de pagamento
     * @throws com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException diferença
     *         a pagar (ou a devolver em dinheiro) sem caixa aberto de {@code username}
     */
    ExchangeResult exchangeItem(Long orderId, ExchangeCommand command, String username);

    /** Resultado de {@link #exchangeItem}: o pedido com o item novo e a troca registrada (para a auditoria). */
    record ExchangeResult(Order order, com.cernecommerce.core.domain.model.pedido.OrderItemExchange exchange) {
    }

    /**
     * Corrige a forma de pagamento de um pedido sem perder o registro do erro (PDV-F030).
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
