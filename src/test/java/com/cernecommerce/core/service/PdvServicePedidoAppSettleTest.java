package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.ecommerce.AtendimentoTransicaoInvalidaException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppOutroCaixaException;
import com.cernecommerce.core.domain.exception.pagamento.InsufficientPaymentException;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.ecommerce.PreferenciaPagamento;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pdv.CashMovementRepository;
import com.cernecommerce.core.ports.out.pdv.CashRegisterRepository;
import com.cernecommerce.core.ports.out.pdv.ComandaRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ECM-F010 — a liquidação do pedido do app exige o aceite, no caixa que aceitou, e cobra a taxa de entrega. */
@ExtendWith(MockitoExtension.class)
class PdvServicePedidoAppSettleTest {

    private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");
    private static final Pricing CARVAO = Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00"));

    @Mock CashRegisterRepository cashRegisterRepository;
    @Mock CashMovementRepository cashMovementRepository;
    @Mock OrderRepository orderRepository;
    @Mock OrderPaymentRepository orderPaymentRepository;
    @Mock EstoqueUseCase estoqueUseCase;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock ComandaRepository comandaRepository;
    @Mock AtendimentoPedidoAppRepository atendimentos;
    ReceivableUseCase receivableUseCase = mock(ReceivableUseCase.class);

    PdvService pdvService;

    @BeforeEach
    void setUp() {
        pdvService = new PdvService(cashRegisterRepository, cashMovementRepository, orderRepository,
                orderPaymentRepository, estoqueUseCase, cashbackUseCase, comandaRepository, BigDecimal.TEN,
                Clock.fixed(AGORA, ZoneOffset.UTC), receivableUseCase, null, atendimentos);
    }

    private static CashRegisterSession caixa(Long id) {
        return CashRegisterSession.of(id, "caixa1", AGORA, BigDecimal.TEN, "LOJA-01",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
    }

    private static List<PaymentCommand> pix(String amount) {
        return List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal(amount), null));
    }

    /** Pedido do app de 2 carvões (44,00), com id, como o checkout o deixa. */
    private static Order pedidoDoApp(OrderDelivery delivery) {
        Order o = Order.openMarketplace(42L, "LOJA-01",
                List.of(OrderItem.fromCatalog("CARV-001", new BigDecimal("2.000"), CARVAO, null)), delivery);
        return new Order(7L, o.orderNumber(), o.channel(), o.status(), o.customerId(), o.sessionId(),
                o.warehouseCode(), o.items(), o.grossAmount(), o.discountAmount(), o.cashbackRedeemed(),
                o.netAmount(), o.changeAmount(), o.cancelReason(), o.createdAt(), o.paidAt(), o.concludedAt(),
                o.cancelledAt(), o.refundedAt(), o.reservedAt(), o.separatedAt(), o.shippedAt(), o.deliveredAt(),
                o.version(), o.comandaId(), o.tableLabel(), o.serviceFeeAmount(), o.delivery());
    }

    private static OrderDelivery retirada() {
        return new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null);
    }

    private static OrderDelivery entregaComTaxa(String fee) {
        return new OrderDelivery(DeliveryType.ENTREGA, new DeliveryAddress("Rua A", "10", null, null, "Centro",
                "Botucatu", "SP", null, null), DeliveryMethod.MOTOBOY_LOJA, null, null, null, null, null,
                new BigDecimal(fee));
    }

    private static AtendimentoPedidoApp pendente(DeliveryType tipo) {
        return AtendimentoPedidoApp.novo(7L, tipo, PreferenciaPagamento.PIX, null, AGORA.plusSeconds(36000), AGORA);
    }

    private void givenPersistence() {
        when(orderRepository.nextOrderNumber()).thenReturn("000001001");
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void retiradaAceitaNoCaixaLiquidaEFechaOAtendimento() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(caixa(1L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedidoDoApp(retirada())));
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.RETIRADA).aceito(1L, "caixa1", AGORA)));
        givenPersistence();

        Order pago = pdvService.settleOnlineOrder(1L, 7L, pix("44.00"), "caixa1");

        assertThat(pago.status()).isEqualTo(OrderStatus.CONCLUIDO);
        assertThat(pago.sessionId()).isEqualTo(1L);
        ArgumentCaptor<AtendimentoPedidoApp> captor = ArgumentCaptor.forClass(AtendimentoPedidoApp.class);
        verify(atendimentos).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AtendimentoStatus.RETIRADO);
        assertThat(captor.getValue().concluidoEm()).isEqualTo(AGORA);
    }

    @Test
    void entregaCobraATaxaDefinidaNoAceiteEContinuaAceitaAteSair() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(caixa(1L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedidoDoApp(entregaComTaxa("8.00"))));
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.ENTREGA).aceito(1L, "caixa1", AGORA)));
        givenPersistence();

        Order pago = pdvService.settleOnlineOrder(1L, 7L, pix("52.00"), "caixa1");

        assertThat(pago.totalPayable()).isEqualByComparingTo("52.00");
        verify(atendimentos, never()).save(any());
    }

    @Test
    void entregaSemATaxaNoPagamentoRecusaAntesDeConsumirAReserva() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(caixa(1L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedidoDoApp(entregaComTaxa("8.00"))));
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.ENTREGA).aceito(1L, "caixa1", AGORA)));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, pix("44.00"), "caixa1"))
                .isInstanceOf(InsufficientPaymentException.class);
        verify(estoqueUseCase, never()).consumeReservationsByOwner(anyString(), anyString());
    }

    @Test
    void pedidoAindaPendenteNaoPodeSerPago() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(caixa(1L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedidoDoApp(retirada())));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(1L, 7L, pix("44.00"), "caixa1"))
                .isInstanceOf(AtendimentoTransicaoInvalidaException.class)
                .hasMessageContaining("aceito");
        verify(estoqueUseCase, never()).consumeReservationsByOwner(anyString(), anyString());
    }

    @Test
    void pedidoAceitoPorOutroCaixaNaoEhLiquidadoAqui() {
        when(cashRegisterRepository.findById(2L)).thenReturn(Optional.of(caixa(2L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedidoDoApp(retirada())));
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.RETIRADA).aceito(1L, "caixa1", AGORA)));

        assertThatThrownBy(() -> pdvService.settleOnlineOrder(2L, 7L, pix("44.00"), "caixa1"))
                .isInstanceOf(PedidoAppOutroCaixaException.class);
    }

    @Test
    void pedidoSemAtendimentoSegueComoAntes() {
        when(cashRegisterRepository.findById(1L)).thenReturn(Optional.of(caixa(1L)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedidoDoApp(null)));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.empty());
        givenPersistence();

        assertThat(pdvService.settleOnlineOrder(1L, 7L, pix("44.00"), "caixa1").status())
                .isEqualTo(OrderStatus.CONCLUIDO);
    }
}
