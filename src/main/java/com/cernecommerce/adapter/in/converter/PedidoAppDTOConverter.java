package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.AtendimentoResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PedidoAppResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.PedidoAppUseCase.PedidoApp;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pedido do app na fila do PDV e no app do cliente (ECM-F010). */
public class PedidoAppDTOConverter {

    private final OrderDTOConverter orderConverter;

    public PedidoAppDTOConverter(OrderDTOConverter orderConverter) {
        this.orderConverter = orderConverter;
    }

    public PedidoAppResponseDTO toResponse(PedidoApp pedidoApp) {
        PedidoAppResponseDTO dto = new PedidoAppResponseDTO();
        OrderResponseDTO pedido = orderConverter.toResponse(pedidoApp.order());
        pedido.setAtendimento(toAtendimento(pedidoApp.atendimento(), true));
        dto.setPedido(pedido);
        dto.setAtendimento(pedido.getAtendimento());
        if (pedidoApp.cliente() != null) {
            dto.setClienteId(pedidoApp.cliente().id());
            dto.setClienteNome(pedidoApp.cliente().nome());
            dto.setClienteContato(pedidoApp.cliente().contato());
            dto.setClienteEmail(pedidoApp.cliente().email());
        }
        return dto;
    }

    public PageResult<PedidoAppResponseDTO> toResponse(PageResult<PedidoApp> page) {
        return new PageResult<>(page.content().stream().map(this::toResponse).toList(), page.page(), page.size(),
                page.totalElements(), page.totalPages());
    }

    /** O pedido como o cliente o vê no app: com a etapa, sem o caixa nem o operador. */
    public OrderResponseDTO toShopResponse(Order order, AtendimentoPedidoApp atendimento) {
        OrderResponseDTO dto = orderConverter.toResponse(order);
        dto.setAtendimento(toAtendimento(atendimento, false));
        return dto;
    }

    public OrderResponseDTO toShopCheckoutResponse(Order order, String checkoutUrl, AtendimentoPedidoApp atendimento) {
        OrderResponseDTO dto = orderConverter.toCheckoutResponse(order, checkoutUrl);
        dto.setAtendimento(toAtendimento(atendimento, false));
        return dto;
    }

    /** Uma página de pedidos do cliente, cada um com o seu atendimento (quando houver). */
    public PageResult<OrderResponseDTO> toShopResponse(PageResult<Order> page, List<AtendimentoPedidoApp> atendimentos) {
        Map<Long, AtendimentoPedidoApp> porPedido = atendimentos.stream()
                .collect(Collectors.toMap(AtendimentoPedidoApp::orderId, Function.identity()));
        return new PageResult<>(page.content().stream().map(o -> toShopResponse(o, porPedido.get(o.id()))).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    private static AtendimentoResponseDTO toAtendimento(AtendimentoPedidoApp a, boolean interno) {
        if (a == null) {
            return null;
        }
        AtendimentoResponseDTO dto = new AtendimentoResponseDTO();
        dto.setStatus(a.status().name());
        dto.setTipo(a.tipo().name());
        dto.setPreferenciaPagamento(a.preferenciaPagamento().name());
        dto.setObservacao(a.observacao());
        dto.setPrazo(a.prazo());
        dto.setMotivo(a.motivo());
        if (interno) {
            dto.setSessionId(a.sessionId());
            dto.setAtendidoPor(a.atendidoPor());
        }
        dto.setCriadoEm(a.criadoEm());
        dto.setAceitoEm(a.aceitoEm());
        dto.setProntoEm(a.prontoEm());
        dto.setSaiuEm(a.saiuEm());
        dto.setConcluidoEm(a.concluidoEm());
        dto.setEncerradoEm(a.encerradoEm());
        return dto;
    }
}
