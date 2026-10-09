package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.adapter.in.converter.CashRegisterDTOConverter;
import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionNotOwnedException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;
import com.cernecommerce.core.domain.model.pedido.DeliveryAddress;
import com.cernecommerce.core.domain.model.pedido.DeliveryMethod;
import com.cernecommerce.core.domain.model.pedido.DeliveryType;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.ReceiptEmailUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class PdvControllerTest {

    private MockMvc mockMvc;
    private PdvUseCase pdvUseCase;
    private ReceiptEmailUseCase receiptEmailUseCase;
    /** Campo desde PDV-F043: o reenvio da mesma venda não pode publicar nada. */
    private ApplicationEventPublisher publisher;
    
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("operador", null, List.of());

    @BeforeEach
    void setup() {
        pdvUseCase = mock(PdvUseCase.class);
        receiptEmailUseCase = mock(ReceiptEmailUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvController(pdvUseCase, new OrderDTOConverter(),
                        new CashRegisterDTOConverter(), publisher, receiptEmailUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listSessions_returns_200() throws Exception {
        when(pdvUseCase.listSessions(any(CashRegisterSessionFilter.class), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/pdv/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    /** PDV-F026 — status, operador e período de abertura chegam ao filtro. */
    @Test
    void listSessions_withFilters_passesThemThrough() throws Exception {
        when(pdvUseCase.listSessions(any(CashRegisterSessionFilter.class), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/pdv/sessions").param("status", "OPEN").param("operator", "caixa1")
                        .param("from", "2026-09-01T00:00:00Z"))
                .andExpect(status().isOk());

        verify(pdvUseCase).listSessions(argThat((CashRegisterSessionFilter f) ->
                f.status() == CashRegisterSession.Status.OPEN && "caixa1".equals(f.operator())
                        && f.from() != null && f.to() == null), eq(0), eq(20));
    }

    private static CashRegisterSession closedByAdmin() {
        return CashRegisterSession.of(1L, "caixa1", Instant.now(), BigDecimal.TEN, "W1", Instant.now(),
                "admin", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO, CashRegisterSession.Status.CLOSED,
                "saiu sem fechar");
    }

    /** PDV-C037 — fechar caixa alheio é a permissão PDV_SESSION_CLOSE_ANY, não a role. */
    @Test
    void closeSession_withCloseAnyPermission_closesAnySessionAndReturnsNotes() throws Exception {
        UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("PDV_SESSION_CLOSE_ANY")));
        when(pdvUseCase.closeSession(eq(1L), any(), eq("saiu sem fechar"), eq("admin"), eq(true)))
                .thenReturn(closedByAdmin());

        mockMvc.perform(post("/pdv/sessions/1/close")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedAmount\":10.00,\"notes\":\"saiu sem fechar\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedBy").value("admin"))
                .andExpect(jsonPath("$.closingNotes").value("saiu sem fechar"));
    }

    /**
     * PDV-C037 — um gerente que não é admin passa a poder receber a conferência: a permissão vale
     * sem nenhuma role privilegiada ao lado.
     */
    @Test
    void closeSession_closeAnyPermissionWithoutAdminRole_closesAnySession() throws Exception {
        UsernamePasswordAuthenticationToken gerente = new UsernamePasswordAuthenticationToken("gerente", null,
                List.of(new SimpleGrantedAuthority("ROLE_GERENTE"), new SimpleGrantedAuthority("PDV_SESSION_CLOSE_ANY")));
        when(pdvUseCase.closeSession(eq(1L), any(), isNull(), eq("gerente"), eq(true))).thenReturn(closedByAdmin());

        mockMvc.perform(post("/pdv/sessions/1/close")
                        .principal(gerente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedAmount\":10.00}"))
                .andExpect(status().isOk());
    }

    /** PDV-C037 — a role sozinha deixou de bastar: sem a permissão, o service recebe canCloseAny=false. */
    @Test
    void closeSession_adminRoleWithoutCloseAnyPermission_cannotCloseAnotherOperatorsSession() throws Exception {
        UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_DEV")));
        when(pdvUseCase.closeSession(eq(1L), any(), isNull(), eq("admin"), eq(false)))
                .thenThrow(new CashRegisterSessionNotOwnedException(1L, "admin"));

        mockMvc.perform(post("/pdv/sessions/1/close")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedAmount\":10.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeSession_atendenteOnAnotherOperatorsSession_returns403() throws Exception {
        UsernamePasswordAuthenticationToken atendente = new UsernamePasswordAuthenticationToken("operador", null,
                List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
        when(pdvUseCase.closeSession(eq(1L), any(), isNull(), eq("operador"), eq(false)))
                .thenThrow(new CashRegisterSessionNotOwnedException(1L, "operador"));

        mockMvc.perform(post("/pdv/sessions/1/close")
                        .principal(atendente)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedAmount\":10.00}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void closeSession_rejectsNotesOver500Chars() throws Exception {
        String notes = "x".repeat(501);
        mockMvc.perform(post("/pdv/sessions/1/close")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedAmount\":10.00,\"notes\":\"" + notes + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openSession_returns_201() throws Exception {
        CashRegisterSession session = CashRegisterSession.of(1L, "operador", Instant.now(), BigDecimal.TEN, "W1",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
        when(pdvUseCase.openSession("operador", new BigDecimal("10.00"), "W1")).thenReturn(session);

        mockMvc.perform(post("/pdv/sessions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":10.00,\"warehouseCode\":\"W1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getCurrentSession_returns_200() throws Exception {
        CashRegisterSession session = CashRegisterSession.of(1L, "operador", Instant.now(), BigDecimal.TEN, "W1",
                null, null, null, null, null, CashRegisterSession.Status.OPEN);
        when(pdvUseCase.getCurrentSession("operador")).thenReturn(session);

        mockMvc.perform(get("/pdv/sessions/current").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // PDV-F008 — reserva para retirada

    private static Order reservedOrder() {
        return Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                .reserved("000001000", null, Instant.now());
    }

    /** PDV-C034 — método que não existe responde 400 com código próprio, não o 400 genérico. */
    @Test
    void registerSale_unknownPaymentMethod_returns_400_INVALID_PAYMENT_METHOD() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"BOLETO","amount":44.00}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAYMENT_METHOD"));

        verify(pdvUseCase, never()).registerSaleIdempotent(any(), any(), any(), any(), anyString(), anyBoolean(), any(), any(), any());
    }

    /** PDV-C034 — GATEWAY_PIX existe no enum, mas não é forma que o operador lança. */
    @Test
    void registerSale_gatewayPix_returns_400_INVALID_PAYMENT_METHOD() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"GATEWAY_PIX","amount":44.00}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAYMENT_METHOD"));

        verify(pdvUseCase, never()).registerSaleIdempotent(any(), any(), any(), any(), anyString(), anyBoolean(), any(), any(), any());
    }

    @Test
    void registerSale_reserveForPickupTrue_repassaAoUseCaseEDevolveReservado() throws Exception {
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(true), isNull(), any(), any()))
                .thenReturn(new PdvUseCase.SaleRegistration(reservedOrder(), false));
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}],
                                 "reserveForPickup":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVADO"))
                .andExpect(jsonPath("$.reservedAt").exists());

        verify(pdvUseCase).registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(true), isNull(), any(), any());
    }

    @Test
    void registerSale_reserveForPickupAusente_repassaFalse() throws Exception {
        Order concluido = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                .concluded("000001000", null, Instant.now());
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(false), isNull(), any(), any()))
                .thenReturn(new PdvUseCase.SaleRegistration(concluido, false));
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        verify(pdvUseCase).registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(false), isNull(), any(), any());
    }

    // PDV-F043 — chave da venda

    /**
     * Reenvio com a mesma chave: 200 com a venda que já existia, e nenhum evento publicado de novo —
     * estoque, cashback e automação de pós-venda já aconteceram no primeiro envio.
     */
    @Test
    void registerSale_reenvioComAMesmaChave_devolve200SemPublicarNada() throws Exception {
        Order existente = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                .concluded("000001000", null, Instant.now());
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(false), isNull(),
                eq("3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b"), isNull()))
                .thenReturn(new PdvUseCase.SaleRegistration(existente, true));
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}],
                                 "clientSaleId":"3f1c9a2e-7b4d-4c1e-9a8f-2d6b5e0c1a7b"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void registerSale_comChaveQueNaoEUuid_responde400() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}],
                                 "clientSaleId":"venda-1"}"""))
                .andExpect(status().isBadRequest());

        verify(pdvUseCase, never()).registerSaleIdempotent(any(), any(), any(), any(), any(), anyBoolean(), any(),
                any(), any());
    }

    // PDV-F022 — entrega, observação por item e caixa por dia

    @Test
    @SuppressWarnings("unchecked")
    void registerSale_comDeliveryENote_repassaAoUseCase() throws Exception {
        Order entrega = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)
                                .withNotes("Sem gelo")),
                        new OrderDelivery(DeliveryType.ENTREGA,
                                new DeliveryAddress("Rua A", "10", null, null, null, "João Pessoa", "PB", null, null),
                                DeliveryMethod.APP_99, null, null, null, null, null, new BigDecimal("8.00")))
                .reserved("000001000", null, Instant.now());
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(false), any(), any(), any()))
                .thenReturn(new PdvUseCase.SaleRegistration(entrega, false));
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2,"note":"Sem gelo"}],
                                 "payments":[{"method":"PIX","amount":52.00}],
                                 "delivery":{"type":"ENTREGA","method":"APP_99","fee":8.00,
                                   "address":{"street":"Rua A","number":"10","city":"João Pessoa","state":"PB"}}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RESERVADO"))
                .andExpect(jsonPath("$.totalPayable").value(52.00))
                .andExpect(jsonPath("$.delivery.type").value("ENTREGA"))
                .andExpect(jsonPath("$.delivery.method").value("APP_99"))
                .andExpect(jsonPath("$.delivery.fee").value(8.00))
                .andExpect(jsonPath("$.delivery.address.city").value("João Pessoa"))
                .andExpect(jsonPath("$.items[0].note").value("Sem gelo"));

        ArgumentCaptor<List<PdvUseCase.SaleItemCommand>> items = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<OrderDelivery> delivery = ArgumentCaptor.forClass(OrderDelivery.class);
        verify(pdvUseCase).registerSaleIdempotent(eq(1L), any(), items.capture(), any(), anyString(), eq(false),
                delivery.capture(), any(), any());
        assertThat(items.getValue().get(0).note()).isEqualTo("Sem gelo");
        assertThat(delivery.getValue().fee()).isEqualByComparingTo("8.00");
        assertThat(delivery.getValue().address().street()).isEqualTo("Rua A");
    }

    @Test
    void registerSale_retiradaComReserveForPickupFalse_eAceita() throws Exception {
        when(pdvUseCase.registerSaleIdempotent(eq(1L), any(), any(), any(), anyString(), eq(false), any(), any(), any()))
                .thenReturn(new PdvUseCase.SaleRegistration(Order.openBalcao(1L, "LOJA-01", null, List.of(
                                com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                        new BigDecimal("2.000"),
                                        com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                                new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)),
                        new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null))
                        .concluded("000001001", null, Instant.now()), false));
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}],
                                 "reserveForPickup":false,
                                 "delivery":{"type":"RETIRADA"}}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));
    }

    @Test
    void registerSale_marcadoSemPermissao_retorna403OnAccountNotAllowed() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":123,"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"MARCADO","amount":44.00,"dueDate":"2099-10-15"}]}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ON_ACCOUNT_NOT_ALLOWED"));
        verifyNoInteractions(pdvUseCase);
    }

    @Test
    void registerSale_marcadoComPermissao_repassaODueDate() throws Exception {
        when(pdvUseCase.registerSaleIdempotent(eq(1L), eq(123L), any(), any(), anyString(), eq(false), isNull(), any(), any()))
                .thenReturn(new PdvUseCase.SaleRegistration(Order.openBalcao(1L, "LOJA-01", 123L, List.of(
                                com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                        new BigDecimal("2.000"),
                                        com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                                new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                        .concluded("000001002", null, Instant.now()), false));
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                "caixa1", null, List.of(new org.springframework.security.core.authority
                                        .SimpleGrantedAuthority("PDV_SALE_ON_ACCOUNT"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":123,"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"PIX","amount":14.00},
                                             {"method":"MARCADO","amount":30.00,"dueDate":"2099-10-15"}]}"""))
                .andExpect(status().isCreated());

        ArgumentCaptor<List<PdvUseCase.PaymentCommand>> payments = ArgumentCaptor.forClass(List.class);
        verify(pdvUseCase).registerSaleIdempotent(eq(1L), eq(123L), any(), payments.capture(), anyString(), eq(false), isNull(), any(), any());
        assertThat(payments.getValue().get(0).dueDate()).isNull();
        assertThat(payments.getValue().get(1).dueDate()).isEqualTo(java.time.LocalDate.of(2099, 10, 15));
    }

    @Test
    void registerSale_entregaSemEndereco_retorna400() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}],
                                 "delivery":{"type":"ENTREGA"}}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DELIVERY"));
    }

    @Test
    void registerSale_noteAcimaDe200_retorna400() throws Exception {
        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"sku\":\"CARV-001\",\"quantity\":2,\"note\":\""
                                + "x".repeat(201) + "\"}],\"payments\":[{\"method\":\"DINHEIRO\",\"amount\":44.00}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void openSession_semOpeningAmount_retorna400() throws Exception {
        mockMvc.perform(post("/pdv/sessions")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"warehouseCode\":\"W1\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(pdvUseCase);
    }
}
