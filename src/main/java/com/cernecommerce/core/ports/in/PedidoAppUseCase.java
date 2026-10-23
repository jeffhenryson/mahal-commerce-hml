package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
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

    /** Um pedido da fila: o atendimento, o pedido com itens e entrega, e quem pediu. */
    record PedidoApp(AtendimentoPedidoApp atendimento, Order order, Customer cliente) {
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

    /** Outro caixa assume um pedido aceito — só quando o caixa que aceitou já fechou, ou é o mesmo operador. */
    PedidoApp assumir(Long orderId, Long sessionId, String username);

    /** Recusa e cancela o pedido, devolvendo a reserva. Vale até o pedido ser pago. */
    PedidoApp recusar(Long orderId, String motivo, String username);

    /** Retirada separada no balcão, esperando o cliente. */
    PedidoApp pronto(Long orderId, String username);

    /** Entrega paga sai da loja. */
    PedidoApp saiuParaEntrega(Long orderId, String username);

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
