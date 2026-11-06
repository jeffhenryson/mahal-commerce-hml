package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.cashback.CashbackRetroactiveNotAllowedException;
import com.cernecommerce.core.domain.exception.cashback.CashbackRetroactiveStaleException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackRetroactivePreview;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CashbackRetroactiveUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.cashback.CashbackRateRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashbackRetroactiveServiceTest {

    private static final Long CLIENTE = 7L;

    @Mock CustomerRepository customerRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock OrderItemRefundRepository orderItemRefundRepository;
    @Mock CashbackEntryRepository cashbackEntryRepository;
    @Mock CashbackRateRepository cashbackRateRepository;
    @Mock CashbackUseCase cashbackUseCase;

    CashbackRetroactiveService service;
    Customer cliente;

    @BeforeEach
    void setUp() {
        service = new CashbackRetroactiveService(customerRepository, orderRepository, orderPaymentRepository,
                orderItemRefundRepository, cashbackEntryRepository, cashbackRateRepository, cashbackUseCase);
        cliente = mock(Customer.class);
        when(cliente.isOfficiallyRegistered()).thenReturn(true);
        when(customerRepository.findById(CLIENTE)).thenReturn(Optional.of(cliente));
        when(cashbackRateRepository.findGlobal(any())).thenReturn(Optional.of(CashbackRate.global(new BigDecimal("3"))));
        when(cashbackUseCase.getSettings()).thenReturn(new CashbackSettings(7, 180, new BigDecimal("50"),
                new BigDecimal("50.00")));
        when(cashbackUseCase.getCustomerBalance(CLIENTE)).thenReturn(mock(CashbackBalance.class));
        when(orderPaymentRepository.findByOrderId(any())).thenReturn(List.of());
        when(orderItemRefundRepository.sumQuantityByOrderItem(any())).thenReturn(Map.of());
    }

    private static OrderItem item(long id, String quantidade, String liquido) {
        OrderItem item = mock(OrderItem.class);
        when(item.id()).thenReturn(id);
        when(item.quantity()).thenReturn(new BigDecimal(quantidade));
        when(item.netAmount()).thenReturn(new BigDecimal(liquido));
        return item;
    }

    private static Order pedido(long id, OrderStatus status, OrderItem... itens) {
        Order order = mock(Order.class);
        when(order.id()).thenReturn(id);
        when(order.status()).thenReturn(status);
        when(order.items()).thenReturn(List.of(itens));
        when(order.paidAt()).thenReturn(Instant.parse("2026-01-10T12:00:00Z").plusSeconds(id));
        when(order.unmarkedFraction(any())).thenReturn(BigDecimal.ONE);
        when(order.cashbackEarningFraction()).thenReturn(BigDecimal.ONE);
        return order;
    }

    private void pedidos(Order... orders) {
        when(orderRepository.findAll(isNull(), isNull(), eq(CLIENTE), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new PageResult<>(List.of(orders), 0, 200, orders.length, 1));
    }

    @Test
    void previa_sem_cpf_avisa_sem_recusar() {
        when(cliente.isOfficiallyRegistered()).thenReturn(false);

        CashbackRetroactivePreview preview = service.preview(CLIENTE);

        assertThat(preview.eligible()).isFalse();
        assertThat(preview.blockReason()).isNotBlank();
    }

    @Test
    void previa_soma_so_pedidos_pagos_sem_ganho_e_do_mais_recente_ao_mais_antigo() {
        Order antigo = pedido(1L, OrderStatus.CONCLUIDO, item(11L, "1", "100.00"));
        Order recente = pedido(2L, OrderStatus.CONCLUIDO, item(21L, "1", "50.00"));
        Order jaGanhou = pedido(3L, OrderStatus.CONCLUIDO, item(31L, "1", "200.00"));
        Order cancelado = pedido(4L, OrderStatus.CANCELADO, item(41L, "1", "300.00"));
        pedidos(antigo, recente, jaGanhou, cancelado);
        when(cashbackEntryRepository.existsEarnedForOrder(3L)).thenReturn(true);

        CashbackRetroactivePreview preview = service.preview(CLIENTE);

        assertThat(preview.creditAmount()).isEqualByComparingTo("4.50");
        assertThat(preview.baseAmount()).isEqualByComparingTo("150.00");
        assertThat(preview.ordersCount()).isEqualTo(2);
        assertThat(preview.skippedAlreadyEarned()).isEqualTo(1);
        assertThat(preview.orders()).extracting(CashbackRetroactivePreview.OrderCredit::orderId)
                .containsExactly(2L, 1L);
    }

    @Test
    void unidades_estornadas_ficam_fora_da_base() {
        pedidos(pedido(1L, OrderStatus.CONCLUIDO, item(11L, "2", "100.00")));
        when(orderItemRefundRepository.sumQuantityByOrderItem(1L)).thenReturn(Map.of(11L, BigDecimal.ONE));

        assertThat(service.preview(CLIENTE).creditAmount()).isEqualByComparingTo("1.50");
    }

    @Test
    void credito_grava_um_lancamento_por_item_ja_disponivel() {
        pedidos(pedido(1L, OrderStatus.CONCLUIDO, item(11L, "1", "100.00"), item(12L, "1", "50.00")));

        CashbackRetroactiveUseCase.Result result = service.grant(CLIENTE, new BigDecimal("4.50"));

        assertThat(result.ordersCredited()).isEqualTo(1);
        assertThat(result.creditedAmount()).isEqualByComparingTo("4.50");
        ArgumentCaptor<CashbackEntry> entries = ArgumentCaptor.forClass(CashbackEntry.class);
        verify(cashbackEntryRepository, times(2)).save(entries.capture());
        assertThat(entries.getAllValues()).extracting(CashbackEntry::orderItemId).containsExactly(11L, 12L);
        verify(cashbackEntryRepository).lockCustomer(CLIENTE);
    }

    @Test
    void valor_diferente_da_previa_nao_grava_nada() {
        pedidos(pedido(1L, OrderStatus.CONCLUIDO, item(11L, "1", "100.00")));

        assertThatThrownBy(() -> service.grant(CLIENTE, new BigDecimal("9.99")))
                .isInstanceOf(CashbackRetroactiveStaleException.class);

        verify(cashbackEntryRepository, never()).save(any());
    }

    @Test
    void sem_pedido_elegivel_responde_nada_a_creditar() {
        pedidos();

        assertThatThrownBy(() -> service.grant(CLIENTE, BigDecimal.ZERO))
                .isInstanceOfSatisfying(CashbackRetroactiveNotAllowedException.class, ex -> assertThat(ex.code())
                        .isEqualTo(CashbackRetroactiveNotAllowedException.NOTHING_TO_CREDIT));
    }
}
