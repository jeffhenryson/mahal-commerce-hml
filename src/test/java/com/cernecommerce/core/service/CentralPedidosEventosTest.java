package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.CentralPedidosEventosUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CentralPedidosEventosTest {

    private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");

    @Mock UserRepository userRepository;
    @Mock NotificationSsePort ssePort;

    /** Uma "transação": acumula tudo e só despacha no {@link #commit()}. */
    private final List<Runnable> pendentes = new ArrayList<>();
    private final AfterCommitExecutor transacao = new AfterCommitExecutor() {
        private final Map<String, List<Object>> itens = new java.util.HashMap<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> void accumulate(String key, T item, Consumer<List<T>> flush) {
            List<Object> lista = itens.get(key);
            if (lista == null) {
                List<Object> nova = new ArrayList<>();
                itens.put(key, nova);
                pendentes.add(() -> flush.accept((List<T>) nova));
                lista = nova;
            }
            lista.add(item);
        }
    };

    CentralPedidosEventos eventos;

    @BeforeEach
    void setUp() {
        eventos = new CentralPedidosEventos(userRepository, ssePort, transacao);
    }

    private void commit() {
        pendentes.forEach(Runnable::run);
        pendentes.clear();
    }

    private static Order pedido(Long id, SalesChannel canal, OrderStatus status) {
        Order o = mock(Order.class);
        lenient().when(o.id()).thenReturn(id);
        lenient().when(o.channel()).thenReturn(canal);
        lenient().when(o.status()).thenReturn(status);
        lenient().when(o.createdAt()).thenReturn(AGORA);
        return o;
    }

    @Test
    void soDepoisDoCommitESoParaQuemEstaConectadoComAPermissao() {
        when(ssePort.connectedUsernames()).thenReturn(Set.of("ana", "bia"));
        when(userRepository.findUsernamesByPermission(CentralPedidosEventosUseCase.PERMISSAO))
                .thenReturn(Set.of("ana", "carla"));

        eventos.pedidoCriado(pedido(7L, SalesChannel.MARKETPLACE, OrderStatus.AGUARDANDO_PAGAMENTO));
        eventos.atendimentoMudou(7L, AtendimentoStatus.PENDENTE, AtendimentoStatus.ACEITO);
        verify(ssePort, never()).sendEvent(anyString(), anyString(), any());

        commit();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(ssePort).sendEvent(eq("ana"), eq("ORDER_CREATED"), payload.capture());
        verify(ssePort).sendEvent(eq("ana"), eq("ORDER_FULFILLMENT_CHANGED"), payload.capture());
        verify(ssePort, never()).sendEvent(eq("bia"), anyString(), any());
        verify(ssePort, never()).sendEvent(eq("carla"), anyString(), any());
        assertThat(payload.getAllValues().get(0)).containsEntry("type", "ORDER_CREATED")
                .containsEntry("orderId", 7L).containsEntry("channel", "MARKETPLACE")
                .containsEntry("createdAt", AGORA.toString());
        assertThat(payload.getAllValues().get(1)).containsEntry("type", "ORDER_FULFILLMENT_CHANGED")
                .containsEntry("channel", "MARKETPLACE").containsEntry("from", "PENDENTE").containsEntry("to", "ACEITO");
    }

    @Test
    void balcaoESemMudancaNaoGeramEvento() {
        eventos.pedidoCriado(pedido(1L, SalesChannel.BALCAO, OrderStatus.CONCLUIDO));
        eventos.pedidoMudouStatus(pedido(2L, SalesChannel.BALCAO, OrderStatus.REEMBOLSADO), OrderStatus.CONCLUIDO);
        eventos.pedidoMudouStatus(pedido(3L, SalesChannel.MESA, OrderStatus.CONCLUIDO), OrderStatus.CONCLUIDO);
        eventos.atendimentoMudou(4L, AtendimentoStatus.ACEITO, AtendimentoStatus.ACEITO);

        assertThat(pendentes).isEmpty();
    }

    @Test
    void ninguemConectadoNemConsultaAPermissao() {
        when(ssePort.connectedUsernames()).thenReturn(Set.of());

        eventos.chamadoCriado(300L, "Mesa 4", "CONTA");
        commit();

        verify(userRepository, never()).findUsernamesByPermission(anyString());
        verify(ssePort, never()).sendEvent(anyString(), anyString(), any());
    }

    @Test
    void mesaMudandoDeStatusLevaOAntesEODepois() {
        when(ssePort.connectedUsernames()).thenReturn(Set.of("ana"));
        when(userRepository.findUsernamesByPermission(CentralPedidosEventosUseCase.PERMISSAO)).thenReturn(Set.of("ana"));

        eventos.pedidoMudouStatus(pedido(9L, SalesChannel.MESA, OrderStatus.REEMBOLSADO), OrderStatus.CONCLUIDO);
        commit();

        verify(ssePort).sendEvent("ana", "ORDER_STATUS_CHANGED", Map.of("type", "ORDER_STATUS_CHANGED",
                "orderId", 9L, "channel", "MESA", "from", "CONCLUIDO", "to", "REEMBOLSADO"));
    }
}
