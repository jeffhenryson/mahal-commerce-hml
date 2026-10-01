package com.cernecommerce.adapter.in.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionStaleException;
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
    
    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("operador", null, List.of());

    @BeforeEach
    void setup() {
        pdvUseCase = mock(PdvUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvController(pdvUseCase, new OrderDTOConverter(),
                        new CashRegisterDTOConverter(), publisher))
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

    @Test
    void closeSession_adminClosesAnySessionAndReturnsNotes() throws Exception {
        UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
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

    @Test
    void closeSession_devAlsoClosesAnySession() throws Exception {
        UsernamePasswordAuthenticationToken dev = new UsernamePasswordAuthenticationToken("dev", null,
                List.of(new SimpleGrantedAuthority("ROLE_DEV")));
        when(pdvUseCase.closeSession(eq(1L), any(), isNull(), eq("dev"), eq(true))).thenReturn(closedByAdmin());

        mockMvc.perform(post("/pdv/sessions/1/close")
                        .principal(dev)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedAmount\":10.00}"))
                .andExpect(status().isOk());
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

    @Test
    void registerSale_reserveForPickupTrue_repassaAoUseCaseEDevolveReservado() throws Exception {
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(true), isNull()))
                .thenReturn(reservedOrder());
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

        verify(pdvUseCase).registerSale(eq(1L), any(), any(), any(), anyString(), eq(true), isNull());
    }

    @Test
    void registerSale_reserveForPickupAusente_repassaFalse() throws Exception {
        Order concluido = Order.openBalcao(1L, "LOJA-01", null, List.of(
                        com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                new BigDecimal("2.000"),
                                com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                        new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)))
                .concluded("000001000", null, Instant.now());
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(false), isNull()))
                .thenReturn(concluido);
        when(pdvUseCase.getOrderPayments(any())).thenReturn(List.of());

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONCLUIDO"));

        verify(pdvUseCase).registerSale(eq(1L), any(), any(), any(), anyString(), eq(false), isNull());
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
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(false), any()))
                .thenReturn(entrega);
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
        verify(pdvUseCase).registerSale(eq(1L), any(), items.capture(), any(), anyString(), eq(false),
                delivery.capture());
        assertThat(items.getValue().get(0).note()).isEqualTo("Sem gelo");
        assertThat(delivery.getValue().fee()).isEqualByComparingTo("8.00");
        assertThat(delivery.getValue().address().street()).isEqualTo("Rua A");
    }

    @Test
    void registerSale_retiradaComReserveForPickupFalse_eAceita() throws Exception {
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(false), any()))
                .thenReturn(Order.openBalcao(1L, "LOJA-01", null, List.of(
                                com.cernecommerce.core.domain.model.pedido.OrderItem.fromCatalog("CARV-001",
                                        new BigDecimal("2.000"),
                                        com.cernecommerce.core.domain.model.estoque.Pricing.of(
                                                new BigDecimal("18.00"), null, new BigDecimal("22.00")), null)),
                        new OrderDelivery(DeliveryType.RETIRADA, null, null, null, null, null, null, null, null))
                        .concluded("000001001", null, Instant.now()));
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
    void registerSale_sessaoDeOntem_retorna409SessionStale() throws Exception {
        when(pdvUseCase.registerSale(eq(1L), any(), any(), any(), anyString(), eq(false), isNull()))
                .thenThrow(new CashRegisterSessionStaleException(1L, LocalDate.of(2026, 9, 25),
                        LocalDate.of(2026, 9, 26)));

        mockMvc.perform(post("/pdv/sessions/1/sales")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"sku":"CARV-001","quantity":2}],
                                 "payments":[{"method":"DINHEIRO","amount":44.00}]}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_STALE"));
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
