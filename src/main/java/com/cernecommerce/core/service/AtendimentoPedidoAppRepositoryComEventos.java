package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.ports.in.CentralPedidosEventosUseCase;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link AtendimentoPedidoAppRepository} que avisa a Central de Pedidos quando a etapa do atendimento
 * muda (ECM-F012). Mesmo raciocínio de {@link OrderRepositoryComEventos}: aceite, separação e
 * despacho estão no {@link PedidoAppService}, mas a retirada fecha na liquidação do caixa e o
 * cancelamento vem do app — todos gravam por aqui.
 */
public class AtendimentoPedidoAppRepositoryComEventos implements AtendimentoPedidoAppRepository {

    private final AtendimentoPedidoAppRepository delegate;
    private final CentralPedidosEventosUseCase eventos;

    public AtendimentoPedidoAppRepositoryComEventos(AtendimentoPedidoAppRepository delegate,
            CentralPedidosEventosUseCase eventos) {
        this.delegate = delegate;
        this.eventos = eventos;
    }

    @Override
    public AtendimentoPedidoApp save(AtendimentoPedidoApp atendimento) {
        AtendimentoStatus antes = delegate.findByOrderId(atendimento.orderId())
                .map(AtendimentoPedidoApp::status).orElse(null);
        AtendimentoPedidoApp salvo = delegate.save(atendimento);
        eventos.atendimentoMudou(salvo.orderId(), antes, salvo.status());
        return salvo;
    }

    @Override
    public Optional<AtendimentoPedidoApp> findByOrderId(Long orderId) {
        return delegate.findByOrderId(orderId);
    }

    @Override
    public Optional<AtendimentoPedidoApp> findByOrderIdForUpdate(Long orderId) {
        return delegate.findByOrderIdForUpdate(orderId);
    }

    @Override
    public List<AtendimentoPedidoApp> findByOrderIds(Collection<Long> orderIds) {
        return delegate.findByOrderIds(orderIds);
    }

    @Override
    public PageResult<AtendimentoPedidoApp> findByStatus(Set<AtendimentoStatus> statuses, Long sessionId, int page,
            int size) {
        return delegate.findByStatus(statuses, sessionId, page, size);
    }

    @Override
    public List<Long> findVencidos(Instant agora, int limite) {
        return delegate.findVencidos(agora, limite);
    }
}
