package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;

/**
 * Eventos em tempo real da Central de Pedidos (ECM-F012): o mínimo para a tela decidir se relê.
 *
 * <p>Saem depois do commit, pelo stream de notificações ({@code GET /notifications/stream}), com o
 * próprio tipo como nome do evento SSE ({@code event: ORDER_CREATED}) e o mesmo tipo em {@code type}
 * no JSON, só para quem está conectado e tem {@value #PERMISSAO}. {@code event: notification} fica
 * reservado às notificações. Nada é gravado: quem estava desconectado relê a tela ao voltar.</p>
 */
public interface CentralPedidosEventosUseCase {

    String PERMISSAO = "ONLINE_ORDER_READ";

    /** {@code ORDER_CREATED}. Balcão é ignorado. */
    void pedidoCriado(Order order);

    /** {@code ORDER_STATUS_CHANGED}. Balcão é ignorado. */
    void pedidoMudouStatus(Order order, OrderStatus de);

    /** {@code ORDER_FULFILLMENT_CHANGED}: o atendimento do pedido do app andou (aceite, separação, despacho). */
    void atendimentoMudou(Long orderId, AtendimentoStatus de, AtendimentoStatus para);

    /** {@code SOLICITACAO_CREATED}. */
    void solicitacaoCriada(Long solicitacaoId, String mesa);

    /** {@code SOLICITACAO_UPDATED}. */
    void solicitacaoAtualizada(Long solicitacaoId, String status);

    /** {@code CHAMADO_CREATED}. */
    void chamadoCriado(Long chamadoId, String mesa, String tipo);

    /** {@code CHAMADO_UPDATED}. */
    void chamadoAtualizado(Long chamadoId, String status);
}
