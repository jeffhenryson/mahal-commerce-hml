package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderFilter;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CentralPedidosEventosUseCase;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link OrderRepository} que avisa a Central de Pedidos a cada pedido criado ou status mudado
 * (ECM-F012).
 *
 * <h2>Por que no save, e não em cada serviço</h2>
 * <p>O status do pedido muda em muitos lugares — liquidação no caixa, webhook do gateway,
 * fechamento de comanda, varredor do prazo, cancelamento, reembolso —, e todos passam por aqui. Pôr
 * o aviso em cada um seria esquecer o próximo. O custo é uma leitura do status gravado antes de
 * cada save de pedido já existente.</p>
 */
public class OrderRepositoryComEventos implements OrderRepository {

    private final OrderRepository delegate;
    private final CentralPedidosEventosUseCase eventos;

    public OrderRepositoryComEventos(OrderRepository delegate, CentralPedidosEventosUseCase eventos) {
        this.delegate = delegate;
        this.eventos = eventos;
    }

    @Override
    public Order save(Order order) {
        OrderStatus antes = order.id() == null ? null : delegate.findStatusById(order.id()).orElse(null);
        Order salvo = delegate.save(order);
        if (antes == null) {
            eventos.pedidoCriado(salvo);
        } else if (antes != salvo.status()) {
            eventos.pedidoMudouStatus(salvo, antes);
        }
        return salvo;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<OrderStatus> findStatusById(Long id) {
        return delegate.findStatusById(id);
    }

    @Override
    public List<Order> findAllByIds(Collection<Long> ids) {
        return delegate.findAllByIds(ids);
    }

    @Override
    public Optional<Long> findIdByClientSaleId(String clientSaleId) {
        return delegate.findIdByClientSaleId(clientSaleId);
    }

    @Override
    public void stampClientSale(Long orderId, String clientSaleId, Instant clientSoldAt) {
        delegate.stampClientSale(orderId, clientSaleId, clientSoldAt);
    }

    @Override
    public Optional<Order> findByIdForUpdate(Long id) {
        return delegate.findByIdForUpdate(id);
    }

    @Override
    public List<Order> findByIds(Collection<Long> ids) {
        return delegate.findByIds(ids);
    }

    @Override
    public List<Order> findByComandaIds(Collection<Long> comandaIds) {
        return delegate.findByComandaIds(comandaIds);
    }

    @Override
    public PageResult<Order> findBySessionId(Long sessionId, int page, int size) {
        return delegate.findBySessionId(sessionId, page, size);
    }

    @Override
    public PageResult<Order> findAll(SalesChannel channel, OrderStatus status, Long customerId, Instant from,
            Instant to, int page, int size) {
        return delegate.findAll(channel, status, customerId, from, to, page, size);
    }

    @Override
    public PageResult<Order> findAll(OrderFilter filter, int page, int size) {
        return delegate.findAll(filter, page, size);
    }

    @Override
    public BigDecimal sumConcludedNetAmountBySessionId(Long sessionId) {
        return delegate.sumConcludedNetAmountBySessionId(sessionId);
    }

    @Override
    public BigDecimal sumChangeAmountBySessionId(Long sessionId) {
        return delegate.sumChangeAmountBySessionId(sessionId);
    }

    @Override
    public String nextOrderNumber() {
        return delegate.nextOrderNumber();
    }
}
