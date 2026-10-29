package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.ecommerce.MotivoRecusa;
import com.cernecommerce.core.domain.model.pedido.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Pedidos do app no caixa (ECM-F010): a fila "Pedidos do app" do PDV, o aceite no caixa aberto,
 * a separação, o despacho e o prazo do dia (ECM-F011).
 *
 * <p>O pagamento não passa por aqui: é a liquidação de sempre ({@code POST
 * /pdv/sessions/{id}/orders/{orderId}/settle}), que passou a exigir o aceite e fecha a retirada.</p>
 */
public interface PedidoAppUseCase {

    /**
     * Um pedido da fila: o atendimento, o pedido com itens e entrega, e quem pediu.
     *
     * @param taxaSugerida ECM-F013 — taxa da Central de Pedidos para o bairro (ou a fixa); só na entrega pendente
     * @param mensagemCliente ECM-F013 — o texto da etapa que o cliente lê, igual ao do aviso
     */
    record PedidoApp(AtendimentoPedidoApp atendimento, Order order, Customer cliente, BigDecimal taxaSugerida,
            String mensagemCliente) {

        public PedidoApp(AtendimentoPedidoApp atendimento, Order order, Customer cliente) {
            this(atendimento, order, cliente, null, null);
        }
    }

    /** O que a fila mostra quando o PDV não filtra: tudo que ainda pede ação da loja. */
    Set<AtendimentoStatus> FILA_PADRAO = Set.of(AtendimentoStatus.PENDENTE, AtendimentoStatus.ACEITO,
            AtendimentoStatus.PRONTO, AtendimentoStatus.SAIU_PARA_ENTREGA);

    /**
     * A fila, do mais antigo ao mais novo. {@code statuses} vazio ou nulo usa {@link #FILA_PADRAO};
     * {@code sessionId} restringe aos aceitos por aquele caixa.
     */
    PageResult<PedidoApp> listar(Set<AtendimentoStatus> statuses, Long sessionId, int page, int size);

    /**
     * @throws com.cernecommerce.core.domain.exception.ecommerce.AtendimentoNotFoundException se o
     *         pedido não for do app
     */
    PedidoApp buscar(Long orderId);

    /**
     * O operador pega o pedido no seu caixa aberto. Na entrega, informa a taxa (zero vale): ela
     * entra no total a pagar, e o cliente a vê no app.
     *
     * @throws com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException se for
     *         entrega sem taxa, ou retirada com taxa
     */
    PedidoApp aceitar(Long orderId, Long sessionId, BigDecimal taxaEntrega, String username);

    /**
     * Aceite pela Central de Pedidos (ECM-F012): o mesmo {@link #aceitar}, com as checagens do
     * {@code POST /orders/{id}/accept}. {@code sessionId} nulo usa o caixa aberto do operador.
     *
     * @throws com.cernecommerce.core.domain.exception.ecommerce.OrderNotMarketplaceException se não
     *         for pedido do app
     * @throws com.cernecommerce.core.domain.exception.ecommerce.OrderNotPendingAcceptException se o
     *         pedido já saiu da fase de aceite
     * @throws com.cernecommerce.core.domain.exception.ecommerce.OrderAlreadyAcceptedException se já
     *         foi aceito
     * @throws com.cernecommerce.core.domain.exception.pdv.NoOpenCashRegisterSessionException sem
     *         {@code sessionId} e sem caixa aberto
     */
    PedidoApp aceitarPedido(Long orderId, Long sessionId, BigDecimal taxaEntrega, String username);

    /**
     * Recusa pela Central de Pedidos (ECM-F012), com código padronizado: as checagens de
     * {@link #aceitarPedido} (menos a do aceite único) e então {@link #recusar}.
     *
     * @throws com.cernecommerce.core.domain.exception.ecommerce.RecusaInvalidaException em
     *         {@link MotivoRecusa#OUTRO} sem nota
     */
    PedidoApp recusarPedido(Long orderId, MotivoRecusa codigo, String nota, String username);

    /** Outro caixa assume um pedido aceito — só quando o caixa que aceitou já fechou, ou é o mesmo operador. */
    PedidoApp assumir(Long orderId, Long sessionId, String username);

    /** Recusa e cancela o pedido, devolvendo a reserva. Vale até o pedido ser pago. */
    PedidoApp recusar(Long orderId, String motivo, String username);

    /**
     * Separado: a retirada esperando o cliente, ou a entrega já paga esperando o entregador (ECM-F013).
     *
     * @throws com.cernecommerce.core.domain.exception.ecommerce.PedidoAppNaoPagoException entrega ainda não paga
     */
    PedidoApp pronto(Long orderId, String username);

    /**
     * Entrega paga sai da loja, direto do aceite ou depois de separada.
     *
     * @param linkViagem ECM-F013 — opcional; vai para {@code delivery.trackingUrl} e para o aviso ao cliente
     */
    PedidoApp saiuParaEntrega(Long orderId, String linkViagem, String username);

    /** Entrega concluída — fecha o atendimento. */
    PedidoApp entregue(Long orderId, String username);

    /** Os atendimentos de uma página de pedidos — o app do cliente mostra a etapa de cada um. */
    List<AtendimentoPedidoApp> atendimentosDe(Collection<Long> orderIds);

    /** Ids dos pedidos com prazo vencido sem pagamento (ECM-F011). */
    List<Long> vencidos(Instant agora, int limite);

    /**
     * Cancela um pedido vencido e devolve a reserva, numa transação própria. Devolve {@code false}
     * quando, sob a trava, ele já não estava vencido (pago, cancelado ou com prazo novo).
     */
    boolean expirar(Long orderId);
}
