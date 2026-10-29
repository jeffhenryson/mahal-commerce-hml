package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.ecommerce.PreferenciaPagamento;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CentralPedidosEventosUseCase;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** ECM-F012 — os decoradores dos repositórios avisam a Central a cada criação e mudança de etapa. */
@ExtendWith(MockitoExtension.class)
class OrderRepositoryComEventosTest {

    private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");

    @Mock OrderRepository orders;
    @Mock AtendimentoPedidoAppRepository atendimentos;
    @Mock CentralPedidosEventosUseCase eventos;

    private static Order pedido(Long id, OrderStatus status) {
        Order o = mock(Order.class);
        org.mockito.Mockito.lenient().when(o.id()).thenReturn(id);
        org.mockito.Mockito.lenient().when(o.status()).thenReturn(status);
        return o;
    }

    @Test
    void pedidoNovoAvisaCriado() {
        Order novo = mock(Order.class);
        when(novo.id()).thenReturn(null); // o mock devolveria 0L
        Order salvo = pedido(7L, OrderStatus.AGUARDANDO_PAGAMENTO);
        when(orders.save(novo)).thenReturn(salvo);

        new OrderRepositoryComEventos(orders, eventos).save(novo);

        verify(orders, never()).findStatusById(any());
        verify(eventos).pedidoCriado(salvo);
    }

    @Test
    void statusMudadoAvisaComOAnterior() {
        Order antes = pedido(7L, OrderStatus.CONCLUIDO);
        Order depois = pedido(7L, OrderStatus.REEMBOLSADO);
        when(orders.findStatusById(7L)).thenReturn(Optional.of(OrderStatus.CONCLUIDO));
        when(orders.save(antes)).thenReturn(depois);

        new OrderRepositoryComEventos(orders, eventos).save(antes);

        verify(eventos).pedidoMudouStatus(depois, OrderStatus.CONCLUIDO);
    }

    @Test
    void saveSemMudarStatusNaoAvisa() {
        Order mesmo = pedido(7L, OrderStatus.AGUARDANDO_PAGAMENTO);
        when(orders.findStatusById(7L)).thenReturn(Optional.of(OrderStatus.AGUARDANDO_PAGAMENTO));
        when(orders.save(mesmo)).thenReturn(mesmo);

        new OrderRepositoryComEventos(orders, eventos).save(mesmo);

        verifyNoInteractions(eventos);
    }

    @Test
    void atendimentoAvisaAEtapaAnteriorEANova() {
        AtendimentoPedidoApp pendente = AtendimentoPedidoApp.novo(7L, DeliveryType.RETIRADA,
                PreferenciaPagamento.PIX, null, AGORA.plusSeconds(3600), AGORA);
        AtendimentoPedidoApp aceito = pendente.aceito(1L, "caixa1", AGORA);
        when(atendimentos.findByOrderId(7L)).thenReturn(Optional.of(pendente));
        when(atendimentos.save(aceito)).thenReturn(aceito);

        new AtendimentoPedidoAppRepositoryComEventos(atendimentos, eventos).save(aceito);

        verify(eventos).atendimentoMudou(7L, AtendimentoStatus.PENDENTE, AtendimentoStatus.ACEITO);
    }
}
