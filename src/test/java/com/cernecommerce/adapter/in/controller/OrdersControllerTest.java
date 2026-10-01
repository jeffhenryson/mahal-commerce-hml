package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.pedido.OrderDelivery;
import com.cernecommerce.core.domain.exception.pedido.OrderHasNoDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import com.cernecommerce.core.domain.exception.pedido.InvalidOrderStatusTransitionException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.exception.pagamento.PaymentTotalMismatchException;
import com.cernecommerce.core.domain.model.pagamento.OrderPaymentCorrection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.response.OrderAdminResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OrderSummaryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.SaleReceiptResponseDTO;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pedido.OrderFilter;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import com.cernecommerce.core.ports.in.OrderUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class OrdersControllerTest {

    private MockMvc mockMvc;
    private OrderUseCase orderUseCase;
    private OrderReportUseCase orderReportUseCase;
    private CrmUseCase crmUseCase;
    private PdvUseCase pdvUseCase;
    private OrderDTOConverter orderConverter;
    private ApplicationEventPublisher publisher;

    private com.cernecommerce.core.ports.in.ReceivableUseCase receivableUseCase;

    private static final UsernamePasswordAuthenticationToken AUTH =
            new UsernamePasswordAuthenticationToken("admin", null, List.of());

    @BeforeEach
    void setup() {
        orderUseCase = mock(OrderUseCase.class);
        orderReportUseCase = mock(OrderReportUseCase.class);
        crmUseCase = mock(CrmUseCase.class);
        pdvUseCase = mock(PdvUseCase.class);
        orderConverter = mock(OrderDTOConverter.class);
        publisher = mock(ApplicationEventPublisher.class);
        receivableUseCase = mock(com.cernecommerce.core.ports.in.ReceivableUseCase.class);
        when(receivableUseCase.findByOrderId(any())).thenReturn(java.util.Optional.empty());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrdersController(orderUseCase, orderReportUseCase, crmUseCase, pdvUseCase, orderConverter,
                        publisher, receivableUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listOrders_returns_200() throws Exception {
        PageResult<Order> orderPage = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(orderUseCase.listOrders(any(OrderFilter.class), eq(0), eq(20)))
                .thenReturn(orderPage);
        
        PageResult<OrderAdminResponseDTO> dtoPage = new PageResult<>(List.of(), 0, 20, 0L, 0);
        when(orderConverter.toAdminResponse(orderPage)).thenReturn(dtoPage);

        mockMvc.perform(get("/orders")
                        .principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** PDV-F026 — filtros de caixa, comanda e número, e os métodos pagos de cada linha. */
    @Test
    void listOrders_bySession_passesTheFilters_andFillsPaymentMethodsInOneCall() throws Exception {
        PageResult<Order> orderPage = new PageResult<>(List.of(), 0, 20, 1L, 1);
        when(orderUseCase.listOrders(any(OrderFilter.class), eq(0), eq(20))).thenReturn(orderPage);
        OrderAdminResponseDTO linha = new OrderAdminResponseDTO();
        linha.setId(5L);
        when(orderConverter.toAdminResponse(orderPage)).thenReturn(new PageResult<>(List.of(linha), 0, 20, 1L, 1));
        when(orderUseCase.getCapturedPaymentMethods(List.of(5L)))
                .thenReturn(Map.of(5L, List.of(PaymentMethod.DINHEIRO, PaymentMethod.PIX)));

        mockMvc.perform(get("/orders").param("sessionId", "7").param("comandaId", "9")
                        .param("orderNumber", "000000123").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paymentMethods[0]").value("DINHEIRO"))
                .andExpect(jsonPath("$.content[0].paymentMethods[1]").value("PIX"));

        verify(orderUseCase).listOrders(argThat((OrderFilter f) -> f.sessionId() == 7L && f.comandaId() == 9L
                && "000000123".equals(f.orderNumber())), eq(0), eq(20));
    }

    @Test
    void getOrder_returns_200() throws Exception {
        Order mockOrder = mock(Order.class);
        when(orderUseCase.getOrder(1L)).thenReturn(mockOrder);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(mockOrder)).thenReturn(mockResponse);

        mockMvc.perform(get("/orders/1")
                        .principal(AUTH))
                .andExpect(status().isOk());
    }

    @Test
    void changeStatus_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.SEPARADO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);
        
        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.ENVIADO);
        when(orderUseCase.changeStatus(1L, OrderStatus.ENVIADO, "admin")).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/status")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENVIADO\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void changeStatusInBulk_cadaPedidoFalhaSozinho() throws Exception {
        Order reservado = mock(Order.class);
        when(reservado.status()).thenReturn(OrderStatus.RESERVADO);
        Order concluido = mock(Order.class);
        when(concluido.status()).thenReturn(OrderStatus.CONCLUIDO);
        when(orderUseCase.getOrder(1L)).thenReturn(reservado);
        when(orderUseCase.changeStatus(1L, OrderStatus.CONCLUIDO, "admin")).thenReturn(concluido);
        when(orderUseCase.getOrder(2L)).thenReturn(concluido);
        when(orderUseCase.changeStatus(2L, OrderStatus.CONCLUIDO, "admin"))
                .thenThrow(new InvalidOrderStatusTransitionException(2L, OrderStatus.CONCLUIDO, OrderStatus.CONCLUIDO));
        when(orderUseCase.getOrder(3L)).thenThrow(new OrderNotFoundException(3L));

        mockMvc.perform(post("/orders/bulk-status")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[1,2,3],\"status\":\"CONCLUIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok[0]").value(1))
                .andExpect(jsonPath("$.failed[0].orderId").value(2))
                .andExpect(jsonPath("$.failed[0].code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.failed[1].orderId").value(3))
                .andExpect(jsonPath("$.failed[1].code").value("ORDER_NOT_FOUND"));

        verify(publisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void changeStatusInBulk_listaVazia_retorna400() throws Exception {
        mockMvc.perform(post("/orders/bulk-status")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderIds\":[],\"status\":\"CONCLUIDO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correctPayments_gerentePassaCanCorrectClosed_eAudita() throws Exception {
        Order order = mock(Order.class);
        when(order.orderNumber()).thenReturn("000001000");
        OrderPaymentCorrection correction = new OrderPaymentCorrection(30L, 7L, "foi débito", "gerente",
                Instant.now(), 1L, true);
        when(orderUseCase.correctPayments(eq(7L), any(), eq("foi débito"), eq("gerente"), eq(true)))
                .thenReturn(new OrderUseCase.PaymentCorrectionResult(order, correction, List.of(), List.of()));
        when(orderConverter.toAdminResponse(order)).thenReturn(new OrderAdminResponseDTO());
        when(orderUseCase.getOrderPayments(7L)).thenReturn(List.of());

        mockMvc.perform(post("/orders/7/payments/correction")
                        .principal(new UsernamePasswordAuthenticationToken("gerente", null,
                                List.of(new SimpleGrantedAuthority("ORDER_PAYMENT_CORRECT_CLOSED"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments":[{"method":"DEBITO","amount":44.00}],"reason":"foi débito"}"""))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void correctPayments_somaDiferente_retorna400PaymentTotalMismatch() throws Exception {
        when(orderUseCase.correctPayments(eq(7L), any(), any(), eq("admin"), eq(false)))
                .thenThrow(new PaymentTotalMismatchException(new BigDecimal("50.00"), new BigDecimal("44.00")));

        mockMvc.perform(post("/orders/7/payments/correction")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payments":[{"method":"DINHEIRO","amount":50.00}],"reason":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_TOTAL_MISMATCH"));
    }

    /** PDV-F008 — o novo valor de enum precisa desserializar e passar pelo controller normalmente. */
    @Test
    void changeStatus_paraReservado_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.CRIADO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);

        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.RESERVADO);
        when(orderUseCase.changeStatus(1L, OrderStatus.RESERVADO, "admin")).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/status")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"RESERVADO\"}"))
                .andExpect(status().isOk());
    }

    /** Retirada de venda reservada: RESERVADO -> CONCLUIDO pelo mesmo endpoint genérico. */
    @Test
    void changeStatus_deReservadoParaConcluido_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.RESERVADO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);

        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.CONCLUIDO);
        when(orderUseCase.changeStatus(1L, OrderStatus.CONCLUIDO, "admin")).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/status")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONCLUIDO\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void cancelOrder_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.CRIADO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);
        
        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.CANCELADO);
        when(orderUseCase.cancelOrder(1L, "Motivo", "admin")).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/cancel")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Motivo\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void refundOrder_returns_200() throws Exception {
        Order before = mock(Order.class);
        when(before.status()).thenReturn(OrderStatus.PAGO);
        when(orderUseCase.getOrder(1L)).thenReturn(before);
        
        Order after = mock(Order.class);
        when(after.status()).thenReturn(OrderStatus.REEMBOLSADO);
        when(orderUseCase.refundOrder(eq(1L), eq("Motivo"), eq("admin"), anyList())).thenReturn(after);

        OrderAdminResponseDTO mockResponse = mock(OrderAdminResponseDTO.class);
        when(orderConverter.toAdminResponse(after)).thenReturn(mockResponse);

        mockMvc.perform(post("/orders/1/refund")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Motivo\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void getSummary_returns_200_withMappedDto() throws Exception {
        OrderSummary summary = new OrderSummary(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                Map.of(), Map.of(), BigDecimal.ZERO, List.of(), List.of());
        when(orderReportUseCase.getSummary(any(), any(), any(), any(), any())).thenReturn(summary);

        OrderSummaryResponseDTO dto = new OrderSummaryResponseDTO();
        dto.setTotalOrders(0);
        when(orderConverter.toSummaryResponse(summary)).thenReturn(dto);

        mockMvc.perform(get("/orders/summary")
                        .principal(AUTH)
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-31T23:59:59Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0));
    }

    @Test
    void getSummary_returns_400_whenFromToMissing() throws Exception {
        mockMvc.perform(get("/orders/summary").principal(AUTH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"));
    }

    @Test
    void getSummary_returns_400_whenPeriodInvalid() throws Exception {
        when(orderReportUseCase.getSummary(any(), any(), any(), any(), any()))
                .thenThrow(new InvalidReportPeriodException("'from' não pode ser depois de 'to'"));

        mockMvc.perform(get("/orders/summary")
                        .principal(AUTH)
                        .param("from", "2026-02-01T00:00:00Z")
                        .param("to", "2026-01-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REPORT_PERIOD"));
    }

    // PDV-F022 — edição da entrega

    @Test
    void updateDelivery_convertsPatchAndReturns200() throws Exception {
        OrderDelivery.Patch patch = new OrderDelivery.Patch(null, null, null, null, null, "1234", "5678", null, null);
        when(orderConverter.toDeliveryPatch(any())).thenReturn(patch);
        Order after = mock(Order.class);
        when(orderUseCase.updateDelivery(1L, patch, "admin")).thenReturn(after);
        when(orderConverter.toAdminResponse(after)).thenReturn(new OrderAdminResponseDTO());

        mockMvc.perform(patch("/orders/1/delivery")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupCode\":\"1234\",\"dropoffCode\":\"5678\"}"))
                .andExpect(status().isOk());

        verify(orderUseCase).updateDelivery(1L, patch, "admin");
        verify(publisher).publishEvent(any(Object.class));
    }

    @Test
    void updateDelivery_withFee_returns400InvalidDelivery() throws Exception {
        when(orderConverter.toDeliveryPatch(any())).thenReturn(
                new OrderDelivery.Patch(null, null, null, null, null, null, null, null, BigDecimal.ONE));
        when(orderUseCase.updateDelivery(eq(1L), any(), eq("admin")))
                .thenThrow(new InvalidDeliveryException("taxa de entrega não pode ser alterada depois da venda"));

        mockMvc.perform(patch("/orders/1/delivery")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fee\":1.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_DELIVERY"));
    }

    @Test
    void updateDelivery_orderWithoutDelivery_returns409() throws Exception {
        when(orderUseCase.updateDelivery(eq(1L), any(), eq("admin")))
                .thenThrow(new OrderHasNoDeliveryException(1L));

        mockMvc.perform(patch("/orders/1/delivery")
                        .principal(AUTH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackingCode\":\"X\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_HAS_NO_DELIVERY"));
    }

    private SaleReceiptResponseDTO receiptWith(Long customerId, Long sessionId) {
        SaleReceiptResponseDTO dto = new SaleReceiptResponseDTO();
        dto.setOrderId(1L);
        dto.setCustomerId(customerId);
        dto.setSessionId(sessionId);
        when(orderConverter.toReceipt(any(), any())).thenReturn(dto);
        return dto;
    }

    @Test
    void getReceipt_enrichesCustomerAndOperator() throws Exception {
        receiptWith(7L, 3L);
        Customer customer = mock(Customer.class);
        when(customer.nome()).thenReturn("Ana");
        when(customer.contato()).thenReturn("(21) 99999-0000");
        when(customer.cpf()).thenReturn("123.456.789-00");
        when(crmUseCase.findCustomerById(7L)).thenReturn(customer);
        CashRegisterSession session = mock(CashRegisterSession.class);
        when(session.operator()).thenReturn("caixa1");
        when(pdvUseCase.getSession(3L)).thenReturn(session);

        mockMvc.perform(get("/orders/1/receipt").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Ana"))
                .andExpect(jsonPath("$.customerPhone").value("(21) 99999-0000"))
                .andExpect(jsonPath("$.customerDocument").value("123.456.789-00"))
                .andExpect(jsonPath("$.operatorName").value("caixa1"));
    }

    @Test
    void getReceipt_stillPrintsWhenCustomerOrSessionIsGone() throws Exception {
        receiptWith(7L, 3L);
        when(crmUseCase.findCustomerById(7L)).thenThrow(new RuntimeException("removido"));
        when(pdvUseCase.getSession(3L)).thenThrow(new RuntimeException("sem sessão"));

        mockMvc.perform(get("/orders/1/receipt").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(7))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.operatorName").doesNotExist());
    }

    @Test
    void getReceipt_anonymousMarketplaceOrder_skipsLookups() throws Exception {
        receiptWith(null, null);

        mockMvc.perform(get("/orders/1/receipt").principal(AUTH)).andExpect(status().isOk());

        verifyNoInteractions(crmUseCase, pdvUseCase);
    }
}
