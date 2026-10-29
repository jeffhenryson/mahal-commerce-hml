package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CentralPedidosEventosUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Empurra os eventos da Central de Pedidos (ECM-F012) depois do commit — mesmo motivo de
 * {@link AvisoOperadores}: a tela relê ao receber, e o que mudou tem de estar lá.
 *
 * <p>Tudo o que uma transação emitiu sai junto, na ordem, num único flush. Os destinatários são
 * quem tem stream aberto <b>e</b> a permissão; sem ninguém conectado nem se consulta a permissão.
 * Falha só é registrada: o evento é conveniência, a tela também relê sozinha.</p>
 *
 * <p>Como o stream de notificações, só alcança quem está conectado <b>nesta</b> instância.</p>
 */
public class CentralPedidosEventos implements CentralPedidosEventosUseCase {

    private static final Logger log = LoggerFactory.getLogger(CentralPedidosEventos.class);
    private static final String CHAVE = "central-pedidos.eventos";

    private final UserRepository userRepository;
    private final NotificationSsePort ssePort;
    private final AfterCommitExecutor afterCommit;

    public CentralPedidosEventos(UserRepository userRepository, NotificationSsePort ssePort,
            AfterCommitExecutor afterCommit) {
        this.userRepository = userRepository;
        this.ssePort = ssePort;
        this.afterCommit = afterCommit;
    }

    @Override
    public void pedidoCriado(Order order) {
        if (!interessa(order)) {
            return;
        }
        emitir(evento("ORDER_CREATED",
                "orderId", order.id(),
                "channel", order.channel().name(),
                "orderNumber", order.orderNumber(),
                "createdAt", order.createdAt() == null ? null : order.createdAt().toString()));
    }

    @Override
    public void pedidoMudouStatus(Order order, OrderStatus de) {
        if (!interessa(order) || de == order.status()) {
            return;
        }
        emitir(evento("ORDER_STATUS_CHANGED",
                "orderId", order.id(),
                "channel", order.channel().name(),
                "from", de == null ? null : de.name(),
                "to", order.status().name()));
    }

    @Override
    public void atendimentoMudou(Long orderId, AtendimentoStatus de, AtendimentoStatus para) {
        if (de == para) {
            return;
        }
        // Só pedido do app tem atendimento; o canal vai junto para a tela filtrar como os ORDER_*.
        emitir(evento("ORDER_FULFILLMENT_CHANGED",
                "orderId", orderId,
                "channel", SalesChannel.MARKETPLACE.name(),
                "from", de == null ? null : de.name(),
                "to", para.name()));
    }

    @Override
    public void solicitacaoCriada(Long solicitacaoId, String mesa) {
        emitir(evento("SOLICITACAO_CREATED", "solicitacaoId", solicitacaoId, "mesa", mesa));
    }

    @Override
    public void solicitacaoAtualizada(Long solicitacaoId, String status) {
        emitir(evento("SOLICITACAO_UPDATED", "solicitacaoId", solicitacaoId, "status", status));
    }

    @Override
    public void chamadoCriado(Long chamadoId, String mesa, String tipo) {
        emitir(evento("CHAMADO_CREATED", "chamadoId", chamadoId, "mesa", mesa, "tipo", tipo));
    }

    @Override
    public void chamadoAtualizado(Long chamadoId, String status) {
        emitir(evento("CHAMADO_UPDATED", "chamadoId", chamadoId, "status", status));
    }

    private static boolean interessa(Order order) {
        return order.channel() == SalesChannel.MARKETPLACE || order.channel() == SalesChannel.MESA;
    }

    /** {@code type} primeiro, depois os pares chave/valor; nulo vai como {@code null} no JSON. */
    private static Map<String, Object> evento(String tipo, Object... pares) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", tipo);
        for (int i = 0; i < pares.length; i += 2) {
            payload.put((String) pares[i], pares[i + 1]);
        }
        return payload;
    }

    private void emitir(Map<String, Object> payload) {
        afterCommit.accumulate(CHAVE, payload, this::enviar);
    }

    private void enviar(List<Map<String, Object>> eventos) {
        try {
            Set<String> conectados = ssePort.connectedUsernames();
            if (conectados.isEmpty()) {
                return;
            }
            Set<String> destinatarios = new HashSet<>(userRepository.findUsernamesByPermission(PERMISSAO));
            destinatarios.retainAll(conectados);
            for (String username : destinatarios) {
                for (Map<String, Object> evento : eventos) {
                    ssePort.sendEvent(username, (String) evento.get("type"), evento);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Eventos da Central de Pedidos não enviados: {}", e.getMessage(), e);
        }
    }
}
