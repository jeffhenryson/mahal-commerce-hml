package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.ecommerce.OrderAlreadyAcceptedException;
import com.cernecommerce.core.domain.exception.ecommerce.OrderNotMarketplaceException;
import com.cernecommerce.core.domain.exception.ecommerce.OrderNotPendingAcceptException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppNaoPagoException;
import com.cernecommerce.core.domain.exception.ecommerce.PedidoAppOutroCaixaException;
import com.cernecommerce.core.domain.exception.ecommerce.RecusaInvalidaException;
import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.InvalidTrackingUrlException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.domain.model.ecommerce.CentralPedidosConfig;
import com.cernecommerce.core.domain.model.ecommerce.MotivoRecusa;
import com.cernecommerce.core.domain.model.ecommerce.PreferenciaPagamento;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.model.pedido.OrderItem;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.ecommerce.AtendimentoPedidoAppRepository;
import com.cernecommerce.core.ports.out.ecommerce.CentralPedidosConfigRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoAppServiceTest {

    private static final Instant AGORA = Instant.parse("2026-10-23T15:00:00Z");
    private static final Instant PRAZO = Instant.parse("2026-10-24T01:00:00Z");
    private static final Long CLIENTE = 42L;

    @Mock AtendimentoPedidoAppRepository atendimentos;
    @Mock OrderRepository orderRepository;
    @Mock OrderUseCase orderUseCase;
    @Mock PdvService pdvService;
    @Mock CrmUseCase crmUseCase;
    @Mock EmailPort emailPort;
    @Mock UserRepository userRepository;
    @Mock NotificationUseCase notificationUseCase;
    @Mock NotificationSsePort ssePort;
    /** ECM-F013 — configuração da Central de Pedidos que o teste troca; começa no padrão. */
    CentralPedidosConfig central = CentralPedidosConfig.padrao();
    final CentralPedidosConfigRepository centralConfig = new CentralPedidosConfigRepository() {
        @Override
        public Optional<CentralPedidosConfig> find() {
            return Optional.of(central);
        }

        @Override
        public CentralPedidosConfig save(CentralPedidosConfig config, String updatedBy) {
            central = config;
            return config;
        }
    };

    PedidoAppService service;

    /** Sem transação real, o executor despacha na hora. */
    private final AfterCommitExecutor imediato = new AfterCommitExecutor() {
        @Override
        public <T> void accumulate(String key, T item, java.util.function.Consumer<List<T>> flush) {
            flush.accept(List.of(item));
        }
    };

    @BeforeEach
    void setUp() {
        AvisoClientePedidoApp avisoCliente = new AvisoClientePedidoApp(crmUseCase, userRepository,
                notificationUseCase, ssePort, emailPort, imediato);
        service = new PedidoAppService(atendimentos, orderRepository, orderUseCase, pdvService, crmUseCase,
                avisoCliente, centralConfig, Clock.fixed(AGORA, ZoneOffset.UTC));
        Customer maria = Customer.of(CLIENTE, "Maria", "14999990000", "maria@x.com", null, "MARKETPLACE", AGORA,
                CustomerStage.NOVO_LEAD);
        lenient().when(crmUseCase.findCustomersByIds(any())).thenReturn(Map.of(CLIENTE, maria));
        lenient().when(atendimentos.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(orderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CashRegisterSession caixa(Long id, String operador, boolean aberto) {
        return aberto
                ? CashRegisterSession.of(id, operador, AGORA, BigDecimal.TEN, "LOJA-01", null, null, null, null, null,
                        CashRegisterSession.Status.OPEN)
                : CashRegisterSession.of(id, operador, AGORA, BigDecimal.TEN, "LOJA-01", AGORA, operador, BigDecimal.TEN,
                        BigDecimal.TEN, BigDecimal.ZERO, CashRegisterSession.Status.CLOSED);
    }

    private static Order pedido(OrderDelivery delivery) {
        Order o = Order.openMarketplace(CLIENTE, "LOJA-01", List.of(OrderItem.fromCatalog("CARV-001",
                new BigDecimal("2.000"), Pricing.of(new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)),
                delivery);
        return new Order(7L, o.orderNumber(), o.channel(), o.status(), o.customerId(), o.sessionId(),
                o.warehouseCode(), o.items(), o.grossAmount(), o.discountAmount(), o.cashbackRedeemed(),
                o.netAmount(), o.changeAmount(), o.cancelReason(), o.createdAt(), o.paidAt(), o.concludedAt(),
                o.cancelledAt(), o.refundedAt(), o.reservedAt(), o.separatedAt(), o.shippedAt(), o.deliveredAt(),
                o.version(), o.comandaId(), o.tableLabel(), o.serviceFeeAmount(), o.delivery());
    }

    private static OrderDelivery entrega() {
        return new OrderDelivery(DeliveryType.ENTREGA, new DeliveryAddress("Rua A", "10", null, null, "Centro",
                "Botucatu", "SP", null, null), DeliveryMethod.MOTOBOY_LOJA, null, null, null, null, null, BigDecimal.ZERO);
    }

    private static OrderDelivery retirada() {
        return new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null);
    }

    private static AtendimentoPedidoApp pendente(DeliveryType tipo) {
        return AtendimentoPedidoApp.novo(7L, tipo, PreferenciaPagamento.PIX, null, PRAZO, AGORA.minusSeconds(60));
    }

    @Test
    void aceitar_entregaGravaATaxaNoPedidoEAvisaOClienteComOTotal() {
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(caixa(1L, "caixa1", true));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.ENTREGA)));
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pedido(entrega())));

        PedidoAppUseCase.PedidoApp aceito = service.aceitar(7L, 1L, new BigDecimal("8.00"), "caixa1");

        assertThat(aceito.atendimento().status()).isEqualTo(AtendimentoStatus.ACEITO);
        assertThat(aceito.atendimento().sessionId()).isEqualTo(1L);
        assertThat(aceito.order().totalPayable()).isEqualByComparingTo("52.00");
        assertThat(aceito.cliente().nome()).isEqualTo("Maria");
        ArgumentCaptor<String> etapa = ArgumentCaptor.forClass(String.class);
        verify(emailPort).sendOrderStatusUpdate(eq("maria@x.com"), any(), etapa.capture());
        assertThat(etapa.getValue()).contains("52.00").contains("Pix");
    }

    @Test
    void aceitar_entregaComChavePixNaCentral_mandaAChaveEAvisaNoApp() {
        central = new CentralPedidosConfig(true, true, List.of(), false, 20, 40, 30, null, true, "pix@mahal.com");
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(caixa(1L, "caixa1", true));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.ENTREGA)));
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pedido(entrega())));
        User conta = User.customer("maria", "hash", "maria@x.com", CLIENTE, Set.of(new Role("ROLE_CUSTOMER")));
        when(userRepository.findByCustomerId(CLIENTE)).thenReturn(Optional.of(conta));
        Notification salva = new Notification(1L, "maria", NotificationType.PEDIDO_CLIENTE, "Pedido #7", "x",
                "/pedidos/7", null, AGORA);
        when(notificationUseCase.notify(eq("maria"), eq(NotificationType.PEDIDO_CLIENTE), eq("Pedido #7"),
                anyString(), eq("/pedidos/7"))).thenReturn(salva);

        PedidoAppUseCase.PedidoApp aceito = service.aceitar(7L, 1L, new BigDecimal("8.00"), "caixa1");

        assertThat(aceito.mensagemCliente()).contains("52.00").contains("pix@mahal.com");
        verify(emailPort).sendOrderStatusUpdate(eq("maria@x.com"), any(), eq(aceito.mensagemCliente()));
        verify(notificationUseCase).notify("maria", NotificationType.PEDIDO_CLIENTE, "Pedido #7",
                aceito.mensagemCliente(), "/pedidos/7");
        verify(ssePort).send("maria", salva);
    }

    @Test
    void listar_entregaPendenteTrazATaxaDoBairroDaCentral() {
        central = new CentralPedidosConfig(true, true, List.of(), false, 20, 40, 30,
                new CentralPedidosConfig.Entrega(true, new BigDecimal("10.00"), BigDecimal.ZERO, null,
                        List.of(new CentralPedidosConfig.TaxaBairro("centro", new BigDecimal("6.00")))),
                true, null);
        when(atendimentos.findByOrderId(7L)).thenReturn(Optional.of(pendente(DeliveryType.ENTREGA)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(entrega())));

        assertThat(service.buscar(7L).taxaSugerida()).isEqualByComparingTo("6.00");
    }

    @Test
    void aceitar_entregaSemTaxaOuRetiradaComTaxaRecusa() {
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(caixa(1L, "caixa1", true));
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.ENTREGA)), Optional.of(pendente(DeliveryType.RETIRADA)));
        when(orderRepository.findByIdForUpdate(7L))
                .thenReturn(Optional.of(pedido(entrega())), Optional.of(pedido(retirada())));

        assertThatThrownBy(() -> service.aceitar(7L, 1L, null, "caixa1")).isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> service.aceitar(7L, 1L, BigDecimal.ONE, "caixa1"))
                .isInstanceOf(InvalidDeliveryException.class);
        verify(atendimentos, never()).save(any());
        verifyNoInteractions(emailPort);
    }

    @Test
    void recusar_cancelaOPedidoESaiDaFilaSemDuplicarOEmailDoEvento() {
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));
        when(orderUseCase.cancelOrder(7L, "Recusado pela loja: acabou", "caixa1"))
                .thenReturn(pedido(retirada()).cancelled("Recusado pela loja: acabou", AGORA));

        PedidoAppUseCase.PedidoApp recusado = service.recusar(7L, "acabou", "caixa1");

        assertThat(recusado.atendimento().status()).isEqualTo(AtendimentoStatus.RECUSADO);
        assertThat(recusado.atendimento().motivo()).isEqualTo("acabou");
        verifyNoInteractions(pdvService, emailPort);
    }

    @Test
    void pronto_naEntregaSoDepoisDePagaESaiDali() {
        AtendimentoPedidoApp aceito = pendente(DeliveryType.ENTREGA).aceito(1L, "caixa1", AGORA);
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(aceito));
        Order naoPago = pedido(entrega());
        Order pago = naoPago.concluded("APP-7", null, AGORA);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(naoPago), Optional.of(pago));

        assertThatThrownBy(() -> service.pronto(7L, "caixa1")).isInstanceOf(PedidoAppNaoPagoException.class);

        PedidoAppUseCase.PedidoApp pronto = service.pronto(7L, "caixa1");
        assertThat(pronto.atendimento().status()).isEqualTo(AtendimentoStatus.PRONTO);
        verify(emailPort).sendOrderStatusUpdate(eq("maria@x.com"), any(),
                eq("Pedido separado, aguardando o entregador"));
    }

    @Test
    void saiuParaEntrega_gravaOLinkDaViagemENoAviso() {
        AtendimentoPedidoApp pronto = new AtendimentoPedidoApp(7L, AtendimentoStatus.PRONTO, DeliveryType.ENTREGA,
                PreferenciaPagamento.PIX, null, PRAZO, 1L, "caixa1", null, null, AGORA, AGORA, AGORA, null, null, null);
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pronto));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(entrega()).concluded("APP-7", null, AGORA)));

        PedidoAppUseCase.PedidoApp saiu = service.saiuParaEntrega(7L, " https://99app.com/t/abc ", "caixa1");

        assertThat(saiu.atendimento().status()).isEqualTo(AtendimentoStatus.SAIU_PARA_ENTREGA);
        assertThat(saiu.order().delivery().trackingUrl()).isEqualTo("https://99app.com/t/abc");
        verify(emailPort).sendOrderStatusUpdate(eq("maria@x.com"), any(),
                eq("Pedido saiu para entrega — acompanhe a viagem: https://99app.com/t/abc"));
    }

    @Test
    void saiuParaEntrega_linkQueNaoEHttpRecusaSemSair() {
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.ENTREGA).aceito(1L, "caixa1", AGORA)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(entrega()).concluded("APP-7", null, AGORA)));

        assertThatThrownBy(() -> service.saiuParaEntrega(7L, "javascript:alert(1)", "caixa1"))
                .isInstanceOf(InvalidTrackingUrlException.class);
        verify(atendimentos, never()).save(any());
    }

    @Test
    void saiuParaEntrega_exigeOPixLiquidado() {
        when(atendimentos.findByOrderIdForUpdate(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.ENTREGA).aceito(1L, "caixa1", AGORA)));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(entrega())));

        assertThatThrownBy(() -> service.saiuParaEntrega(7L, null, "caixa1")).isInstanceOf(PedidoAppNaoPagoException.class);
        verify(pdvService).requireOwnOpenSession(1L, "caixa1");
        verify(atendimentos, never()).save(any());
    }

    @Test
    void assumir_soQuandoOCaixaQueAceitouFechou() {
        AtendimentoPedidoApp aceitoNoCaixa1 = pendente(DeliveryType.RETIRADA).aceito(1L, "ana", AGORA);
        when(pdvService.requireOwnOpenSession(2L, "bia")).thenReturn(caixa(2L, "bia", true));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(aceitoNoCaixa1));
        when(pdvService.getSession(1L)).thenReturn(caixa(1L, "ana", true), caixa(1L, "ana", false));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(retirada())));

        assertThatThrownBy(() -> service.assumir(7L, 2L, "bia")).isInstanceOf(PedidoAppOutroCaixaException.class);

        PedidoAppUseCase.PedidoApp assumido = service.assumir(7L, 2L, "bia");
        assertThat(assumido.atendimento().sessionId()).isEqualTo(2L);
        assertThat(assumido.atendimento().atendidoPor()).isEqualTo("bia");
    }

    @Test
    void expirar_cancelaOVencidoNaoPagoEAvisaOCliente() {
        AtendimentoPedidoApp vencido = AtendimentoPedidoApp.novo(7L, DeliveryType.RETIRADA, PreferenciaPagamento.PIX,
                null, AGORA.minusSeconds(1), AGORA.minusSeconds(3600));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(vencido));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(retirada())));
        when(orderUseCase.cancelOrder(eq(7L), anyString(), eq("sistema")))
                .thenReturn(pedido(retirada()).cancelled("prazo", AGORA));

        assertThat(service.expirar(7L)).isTrue();

        ArgumentCaptor<AtendimentoPedidoApp> captor = ArgumentCaptor.forClass(AtendimentoPedidoApp.class);
        verify(atendimentos).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(AtendimentoStatus.EXPIRADO);
        verify(emailPort).sendOrderCancellation(eq("maria@x.com"), any(), anyString(), eq(false));
    }

    @Test
    void expirar_ignoraQuemGanhouPrazoOuJaFoiPagoDepoisDaBusca() {
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));

        assertThat(service.expirar(7L)).isFalse();
        verifyNoInteractions(orderUseCase);
    }

    @Test
    void listar_semFiltroUsaAFilaPadraoEMontaComClienteEPedido() {
        when(atendimentos.findByStatus(PedidoAppUseCase.FILA_PADRAO, null, 0, 20))
                .thenReturn(new PageResult<>(List.of(pendente(DeliveryType.RETIRADA)), 0, 20, 1L, 1));
        when(orderRepository.findByIds(List.of(7L))).thenReturn(List.of(pedido(retirada())));

        PageResult<PedidoAppUseCase.PedidoApp> fila = service.listar(null, null, 0, 20);

        assertThat(fila.content()).hasSize(1);
        assertThat(fila.content().get(0).order().id()).isEqualTo(7L);
        assertThat(fila.content().get(0).cliente().contato()).isEqualTo("14999990000");
    }

    // ── ECM-F012: aceite e recusa pela Central de Pedidos ──

    private static Order outroPedido(SalesChannel canal, OrderStatus status) {
        Order o = org.mockito.Mockito.mock(Order.class);
        lenient().when(o.channel()).thenReturn(canal);
        lenient().when(o.status()).thenReturn(status);
        return o;
    }

    @Test
    void aceitarPedido_semSessionIdUsaOCaixaAbertoDoOperador() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(retirada())));
        when(atendimentos.findByOrderId(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));
        when(pdvService.getCurrentSession("caixa1")).thenReturn(caixa(1L, "caixa1", true));
        when(pdvService.requireOwnOpenSession(1L, "caixa1")).thenReturn(caixa(1L, "caixa1", true));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));
        when(orderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(pedido(retirada())));

        PedidoAppUseCase.PedidoApp aceito = service.aceitarPedido(7L, null, null, "caixa1");

        assertThat(aceito.atendimento().status()).isEqualTo(AtendimentoStatus.ACEITO);
        assertThat(aceito.atendimento().sessionId()).isEqualTo(1L);
        assertThat(aceito.atendimento().atendidoPor()).isEqualTo("caixa1");
    }

    @Test
    void aceitarPedido_soUmaVez() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(retirada())));
        when(atendimentos.findByOrderId(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.RETIRADA).aceito(1L, "ana", AGORA)));

        assertThatThrownBy(() -> service.aceitarPedido(7L, 1L, null, "caixa1"))
                .isInstanceOf(OrderAlreadyAcceptedException.class);
        verifyNoInteractions(pdvService);
        verify(atendimentos, never()).save(any());
    }

    @Test
    void aceitarPedido_soPedidoDoApp() {
        Order daMesa = outroPedido(SalesChannel.MESA, OrderStatus.CONCLUIDO);
        when(orderRepository.findById(7L)).thenReturn(Optional.of(daMesa));

        assertThatThrownBy(() -> service.aceitarPedido(7L, 1L, null, "caixa1"))
                .isInstanceOf(OrderNotMarketplaceException.class);
        verifyNoInteractions(atendimentos, pdvService);
    }

    @Test
    void aceitarPedido_pedidoJaLiquidadoOuAtendimentoEncerradoNaoEstaAguardandoAceite() {
        Order liquidado = outroPedido(SalesChannel.MARKETPLACE, OrderStatus.CONCLUIDO);
        when(orderRepository.findById(7L))
                .thenReturn(Optional.of(liquidado), Optional.of(pedido(retirada())));
        when(atendimentos.findByOrderId(7L))
                .thenReturn(Optional.of(pendente(DeliveryType.RETIRADA).recusado("acabou", AGORA)));

        assertThatThrownBy(() -> service.aceitarPedido(7L, 1L, null, "caixa1"))
                .isInstanceOf(OrderNotPendingAcceptException.class)
                .hasMessageContaining("CONCLUIDO");
        assertThatThrownBy(() -> service.aceitarPedido(7L, 1L, null, "caixa1"))
                .isInstanceOf(OrderNotPendingAcceptException.class)
                .hasMessageContaining("RECUSADO");
        verifyNoInteractions(pdvService);
    }

    @Test
    void recusarPedido_gravaOCodigoEOMotivoComANota() {
        when(orderRepository.findById(7L)).thenReturn(Optional.of(pedido(retirada())));
        when(atendimentos.findByOrderId(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));
        when(atendimentos.findByOrderIdForUpdate(7L)).thenReturn(Optional.of(pendente(DeliveryType.RETIRADA)));
        String motivo = "Sem estoque: acabou a menta";
        when(orderUseCase.cancelOrder(7L, "Recusado pela loja: " + motivo, "caixa1"))
                .thenReturn(pedido(retirada()).cancelled("Recusado pela loja: " + motivo, AGORA));

        PedidoAppUseCase.PedidoApp recusado = service.recusarPedido(7L, MotivoRecusa.SEM_ESTOQUE, " acabou a menta ",
                "caixa1");

        assertThat(recusado.atendimento().status()).isEqualTo(AtendimentoStatus.RECUSADO);
        assertThat(recusado.atendimento().motivoCodigo()).isEqualTo(MotivoRecusa.SEM_ESTOQUE);
        assertThat(recusado.atendimento().motivo()).isEqualTo(motivo);
        assertThat(recusado.atendimento().encerradoEm()).isEqualTo(AGORA);
    }

    @Test
    void recusarPedido_outroSemNotaNaoCancelaNada() {
        assertThatThrownBy(() -> service.recusarPedido(7L, MotivoRecusa.OUTRO, "  ", "caixa1"))
                .isInstanceOf(RecusaInvalidaException.class);
        verifyNoInteractions(orderUseCase, atendimentos);
    }
}
