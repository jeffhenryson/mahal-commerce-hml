package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.pagamento.PaymentGatewayException;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase;
import com.cernecommerce.core.ports.in.PaymentWebhookUseCase.WebhookResult;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class PaymentWebhookControllerTest {

    private MockMvc mockMvc;
    private PaymentWebhookUseCase paymentWebhookUseCase;
    private ApplicationEventPublisher publisher;

    @BeforeEach
    void setup() {
        paymentWebhookUseCase = mock(PaymentWebhookUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PaymentWebhookController(paymentWebhookUseCase, publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void receive_success_returns_200_and_publishesEventIfPaid() throws Exception {
        when(paymentWebhookUseCase.handleNotification("order_123", "txn_456", "inv_789"))
                .thenReturn(new WebhookResult(true, 100L, "2024-001"));

        mockMvc.perform(post("/webhooks/payments/infinitepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_nsu\":\"order_123\",\"transaction_nsu\":\"txn_456\",\"invoice_slug\":\"inv_789\"}"))
                .andExpect(status().isOk());

        verify(publisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void receive_success_returns_200_and_doesNotPublishEventIfNotPaid() throws Exception {
        when(paymentWebhookUseCase.handleNotification("order_123", "txn_456", "inv_789"))
                .thenReturn(WebhookResult.noop());

        mockMvc.perform(post("/webhooks/payments/infinitepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_nsu\":\"order_123\",\"transaction_nsu\":\"txn_456\",\"invoice_slug\":\"inv_789\"}"))
                .andExpect(status().isOk());

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void receive_gatewayException_returns_400() throws Exception {
        when(paymentWebhookUseCase.handleNotification(anyString(), anyString(), anyString()))
                .thenThrow(new PaymentGatewayException("Timeout na reconsulta ao InfinitePay", null));

        mockMvc.perform(post("/webhooks/payments/infinitepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_nsu\":\"order_123\",\"transaction_nsu\":\"txn_456\",\"invoice_slug\":\"inv_789\"}"))
                .andExpect(status().isBadRequest());

        // Só o alerta de falha — nenhum ORDER_STATUS_CHANGED.
        verify(publisher).publishEvent(argThat((Object e) -> e instanceof AuditEvent ev
                && ev.type() == AuditEvent.EventType.PAYMENT_WEBHOOK_FAILED));
        verifyNoMoreInteractions(publisher);
    }
    
    @Test
    void receive_runtimeException_returns_400() throws Exception {
        when(paymentWebhookUseCase.handleNotification(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Algum erro inesperado de banco"));

        mockMvc.perform(post("/webhooks/payments/infinitepay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"order_nsu\":\"order_123\",\"transaction_nsu\":\"txn_456\",\"invoice_slug\":\"inv_789\"}"))
                .andExpect(status().isBadRequest());

        // Só o alerta de falha — nenhum ORDER_STATUS_CHANGED.
        verify(publisher).publishEvent(argThat((Object e) -> e instanceof AuditEvent ev
                && ev.type() == AuditEvent.EventType.PAYMENT_WEBHOOK_FAILED));
        verifyNoMoreInteractions(publisher);
    }
}
