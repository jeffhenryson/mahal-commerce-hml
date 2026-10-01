package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.exception.pdv.InvalidPaymentChannelException;
import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashMovementType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Port de entrada do domínio <b>vendas-balcao (PDV)</b>.
 *
 * <p>O ciclo de caixa (PDV-F001/F002) espelha o balanço de inventário: o fechamento confronta
 * contado × esperado, carimba a divergência e <b>fecha mesmo assim</b>.</p>
 */
public interface PdvUseCase {

    // ── Ciclo de caixa ───────────────────────────────────────────────────────────────────────

    /** Lista as sessões de caixa paginadas. */
    PageResult<CashRegisterSession> listSessions(int page, int size);

    /** PDV-F026 — sessões filtradas por status, operador e período de abertura, mais recentes primeiro. */
    PageResult<CashRegisterSession> listSessions(CashRegisterSessionFilter filter, int page, int size);

    /**
     * Abre um caixa para o operador.
     *
     * @param warehouseCode depósito de onde sairá a mercadoria vendida neste caixa. Fica na sessão
     *        para o operador não baixar estoque de depósito alheio (PDV-C004)
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionAlreadyOpenException
     *         se o operador já tiver um caixa aberto
     * @throws com.cernecommerce.core.domain.exception.estoque.WarehouseNotFoundException
     *         se o depósito não existir
     */
    CashRegisterSession openSession(String operator, BigDecimal openingAmount, String warehouseCode);

    /**
     * Caixa aberto do operador.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException
     *         se não houver nenhum
     */
    CashRegisterSession getCurrentSession(String operator);

    /**
     * Busca uma sessão pelo id.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException
     *         se não existir
     */
    CashRegisterSession getSession(Long sessionId);

    /**
     * PED-F003 — quem operava cada caixa, em lote: é o funcionário que fez a venda na lista de
     * pedidos. Sessões inexistentes ficam fora do mapa.
     */
    Map<Long, String> getSessionOperators(Collection<Long> sessionIds);

    /**
     * Registra sangria ou suprimento. Exige sessão aberta <b>e do próprio operador</b>.
     *
     * @param amount sempre positivo — o sentido vem de {@code type}
     */
    CashMovement registerCashMovement(Long sessionId, CashMovementType type, BigDecimal amount,
            String reason, String username);

    /** Movimentos de uma sessão, na ordem em que aconteceram. Paginado desde PDV-C012. */
    PageResult<CashMovement> listCashMovements(Long sessionId, int page, int size);

    /**
     * Fecha o caixa confrontando o contado com o esperado.
     *
     * <p><b>Divergência não bloqueia</b> — é registrada, exatamente como no fechamento de um balanço
     * de inventário. Recusar o fechamento só produziria caixas que nunca fecham.</p>
     *
     * <p>Este overload é o fechamento privilegiado (sem checagem de dono), usado internamente e por
     * quem já tem a decisão de acesso tomada. A API usa
     * {@link #closeSession(Long, BigDecimal, String, String, boolean)}.</p>
     */
    default CashRegisterSession closeSession(Long sessionId, BigDecimal countedAmount, String username) {
        return closeSession(sessionId, countedAmount, null, username, true);
    }

    /**
     * Fecha o caixa com motivo opcional.
     *
     * <p>Fechar o caixa de <b>outro</b> operador é conferência de gerente: só quem tem
     * {@code canCloseAny} (admin/dev) pode. O atendente, que também tem {@code PDV_SESSION_CLOSE},
     * fecha só o próprio caixa.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         quem não tem {@code canCloseAny} tentou fechar o caixa de outro operador (403)
     */
    CashRegisterSession closeSession(Long sessionId, BigDecimal countedAmount, String notes, String username,
            boolean canCloseAny);

    /**
     * Totais recebidos na sessão, agrupados por forma de pagamento — só pagamento {@code CAPTURED}
     * conta. É o detalhamento que acompanha o fechamento: <b>só {@code DINHEIRO} entra na
     * conferência da gaveta</b>; débito, crédito e PIX vão para conferência contra a adquirente,
     * não contra o contado no caixa.
     *
     * <p>Devolve as quatro formas sempre, mesmo com total zero — forma que a sessão nunca usou não
     * desaparece da lista, só aparece zerada.</p>
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotFoundException
     *         se a sessão não existir
     */
    List<PaymentTotal> getSessionPaymentTotals(Long sessionId);

    // ── Venda ────────────────────────────────────────────────────────────────────────────────

    /**
     * Registra uma venda de balcão, captura o(s) pagamento(s) e dá baixa no estoque, tudo na mesma
     * transação (PDV-F003/F004/F006).
     *
     * <p>O preço e o custo de cada item vêm do <b>catálogo</b>, nunca do chamador. O depósito vem da
     * <b>sessão</b>, nunca do request. Saldo insuficiente em qualquer item reverte a venda inteira.</p>
     *
     * <p>Pagamento é validado <b>antes</b> de tocar o estoque: {@code payments} tem que somar pelo
     * menos o líquido do pedido, e a parte que não é {@code DINHEIRO} não pode sozinha passar do
     * líquido — só dinheiro pode ser tendido a mais para virar troco. O troco entra no pedido
     * concluído, nunca como uma linha de pagamento.</p>
     *
     * @param customerId cliente identificado, ou {@code null} — a venda anônima de passagem é o caso
     *        normal do balcão
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está vendendo
     * @throws com.cernecommerce.core.domain.exception.pedido.ProductNotPricedException
     *         se algum item não tiver preço no catálogo
     * @throws com.cernecommerce.core.domain.exception.pedido.DiscountLimitExceededException
     *         se o desconto total passar do teto configurado
     * @throws com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException
     *         se a soma dos pagamentos não cobrir o líquido do pedido
     * @throws com.cernecommerce.core.domain.exception.pagamento.PaymentExceedsOrderTotalException
     *         se a soma dos pagamentos que não são {@code DINHEIRO} passar do líquido do pedido
     * @throws com.cernecommerce.core.domain.exception.estoque.InsufficientStockException
     *         se o saldo de algum item for insuficiente
     */
    default Order registerSale(Long sessionId, Long customerId, List<SaleItemCommand> items,
            List<PaymentCommand> payments, String username) {
        return registerSale(sessionId, customerId, items, payments, username, false);
    }

    /**
     * Registra a venda já decidindo o destino: {@code reserveForPickup=false} conclui na hora
     * (comportamento de sempre); {@code true} grava {@link OrderStatus#RESERVADO} em vez de
     * {@link OrderStatus#CONCLUIDO} (PDV-F008) — mercadoria já baixada do estoque e pagamento já
     * capturado, exatamente como uma venda concluída, só que o cliente ainda não levou.
     */
    default Order registerSale(Long sessionId, Long customerId, List<SaleItemCommand> items,
            List<PaymentCommand> payments, String username, boolean reserveForPickup) {
        return registerSale(sessionId, customerId, items, payments, username, reserveForPickup, null);
    }

    /**
     * Registra a venda com entrega ou retirada (PDV-F022). Com {@code delivery}, a venda sempre
     * nasce {@link OrderStatus#RESERVADO}; a taxa de entrega entra no total a pagar (fora do
     * líquido) e o pagamento é validado contra ele. Único método abstrato — as sobrecargas acima
     * delegam até aqui.
     *
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionStaleException
     *         se a sessão foi aberta num dia anterior (data de America/Sao_Paulo)
     */
    Order registerSale(Long sessionId, Long customerId, List<SaleItemCommand> items,
            List<PaymentCommand> payments, String username, boolean reserveForPickup,
            OrderDelivery delivery);

    /** Pagamentos de um pedido, na ordem em que foram lançados (PDV-F006). */
    List<OrderPayment> getOrderPayments(Long orderId);

    /**
     * Busca um pedido pelo id (PDV-F005).
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se não existir
     */
    Order getOrder(Long orderId);

    /** Pedidos de uma sessão de caixa, do mais recente para o mais antigo (PDV-F005). */
    PageResult<Order> listSessionOrders(Long sessionId, int page, int size);

    /**
     * Pedidos feitos no aplicativo que ainda aguardam pagamento — a lista que o caixa consulta
     * quando o cliente chega à loja para retirar e pagar.
     */
    PageResult<Order> listPendingOnlineOrders(int page, int size);

    /**
     * Liquida no balcão um pedido montado no aplicativo: recebe o pagamento, entrega a mercadoria e
     * conclui.
     *
     * <p>O canal <b>continua</b> {@code MARKETPLACE} — foi o site que gerou a venda, e é assim que
     * ela tem que aparecer no relatório de conversão. O que muda é o {@code sessionId}, que passa a
     * apontar para o caixa que recebeu o dinheiro, de modo que o fechamento daquela gaveta o
     * contabilize.</p>
     *
     * <p>O estoque desse pedido <b>já está reservado</b> desde o checkout, então a liquidação
     * <b>consome a reserva</b> em vez de dar baixa nova — dar baixa aqui debitaria a mercadoria duas
     * vezes.</p>
     *
     * <p><b>O pagamento recebido é registrado</b> (PDV-C015), como em qualquer outro recebimento do
     * módulo: sem isso o dinheiro entrava na gaveta e o ledger não sabia, e o fechamento daquele
     * caixa acusava sobra sem dono. A cobrança de gateway aberta no checkout é encerrada na mesma
     * transação — pago no balcão, nenhum webhook vai confirmá-la.</p>
     *
     * <p><b>Valor exato, sem troco:</b> o canal permanece {@code MARKETPLACE} e {@code Order} não
     * admite {@code changeAmount} ali. O operador lança o que fica na gaveta.</p>
     *
     * @param payments pelo menos uma linha, mesmo shape da venda de balcão — várias linhas =
     *        pagamento dividido
     * @throws com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException se o pedido não
     *         existir
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException
     *         se o pedido não estiver aguardando pagamento
     * @throws com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException
     *         se a sessão não pertencer a quem está liquidando
     * @throws com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException
     *         se a soma dos pagamentos não cobrir o líquido do pedido
     * @throws com.cernecommerce.core.domain.exception.pagamento.ChangeNotSupportedException
     *         se a soma passar do líquido — aqui não há onde guardar troco
     */
    Order settleOnlineOrder(Long sessionId, Long orderId, List<PaymentCommand> payments, String username);

    /**
     * O que o chamador informa por item de venda: <b>não</b> inclui preço.
     *
     * <p>É a diferença central de PDV-F004. Antes, {@code SaleItemRequest.unitPrice} era digitado
     * pelo cliente HTTP, e quem tivesse {@code PDV_SALE_MANAGE} vendia qualquer coisa por qualquer
     * valor sem deixar trilha de desconto.</p>
     */
    record SaleItemCommand(String sku, BigDecimal quantity, BigDecimal discountAmount, String note) {

        /** Sem observação — forma anterior a PDV-F022. */
        public SaleItemCommand(String sku, BigDecimal quantity, BigDecimal discountAmount) {
            this(sku, quantity, discountAmount, null);
        }
    }

    /**
     * O que o chamador informa por linha de pagamento (PDV-F006).
     *
     * @param installments só faz sentido com {@code method == CREDITO}; {@code null} nos demais
     */
    record PaymentCommand(PaymentMethod method, BigDecimal amount, Integer installments,
            PaymentChannel channel, PaymentProvider provider, java.time.LocalDate dueDate) {

        /**
         * PDV-F025 — recusa na borda, com código próprio, o que o CHECK da V134 recusaria como 500:
         * DINHEIRO não passa por maquininha nem link, e operadora sem canal não diz por onde saiu.
         */
        public PaymentCommand {
            if (method == PaymentMethod.DINHEIRO && (channel != null || provider != null)) {
                throw new InvalidPaymentChannelException("Pagamento em DINHEIRO não tem canal nem operadora");
            }
            if (provider != null && channel == null) {
                throw new InvalidPaymentChannelException("Informe o canal (MAQUININHA ou LINK) junto com a operadora");
            }
        }

        public PaymentCommand(PaymentMethod method, BigDecimal amount, Integer installments,
                PaymentChannel channel, PaymentProvider provider) {
            this(method, amount, installments, channel, provider, null);
        }

        public PaymentCommand(PaymentMethod method, BigDecimal amount, Integer installments) {
            this(method, amount, installments, null, null, null);
        }

        /** CRM-F010 — a linha MARCADO da venda: sem canal, sem parcela, com vencimento. */
        public static PaymentCommand onAccount(BigDecimal amount, java.time.LocalDate dueDate) {
            return new PaymentCommand(PaymentMethod.MARCADO, amount, null, null, null, dueDate);
        }

        public boolean isOnAccount() {
            return method == PaymentMethod.MARCADO;
        }
    }

    /**
     * Total recebido por forma de pagamento numa sessão.
     *
     * @param amount bruto: soma dos {@code CAPTURED} — em DINHEIRO é o valor entregue, troco dentro
     * @param refundedAmount soma dos estornos ({@code REFUNDED}) do método (PDV-F026)
     * @param changeAmount troco devolvido na sessão; só em DINHEIRO, zero nos demais (PDV-F026)
     * @param netAmount o que ficou: {@code amount + receivableReceived - refundedAmount - changeAmount}
     *        (PDV-F026). Em DINHEIRO é a mesma conta do esperado de {@code closeSession}, sem o fundo
     *        e os movimentos
     * @param receivableReceived quitação de marcado recebida nesta sessão (CRM-F010) — separada da
     *        venda para o relatório do caixa; já é o abatido, líquido do troco devolvido
     */
    record PaymentTotal(PaymentMethod method, BigDecimal amount, BigDecimal refundedAmount,
            BigDecimal changeAmount, BigDecimal netAmount, BigDecimal receivableReceived) {

        public PaymentTotal(PaymentMethod method, BigDecimal amount, BigDecimal refundedAmount,
                BigDecimal changeAmount, BigDecimal netAmount) {
            this(method, amount, refundedAmount, changeAmount, netAmount, BigDecimal.ZERO);
        }

        public PaymentTotal(PaymentMethod method, BigDecimal amount) {
            this(method, amount, BigDecimal.ZERO, BigDecimal.ZERO, amount, BigDecimal.ZERO);
        }
    }
}
