package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ReceiptEmailUnavailableException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderItemRefund;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptEmailServiceTest {

    private OrderRepository orderRepository;
    private CustomerRepository customerRepository;
    private EmailPort emailPort;
    private OrderPaymentRepository payments;
    private OrderItemRefundRepository refunds;
    private CashbackEntryRepository cashback;
    private CustomerReceivableRepository receivables;
    private ReceiptEmailService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        customerRepository = mock(CustomerRepository.class);
        emailPort = mock(EmailPort.class);
        payments = mock(OrderPaymentRepository.class);
        refunds = mock(OrderItemRefundRepository.class);
        cashback = mock(CashbackEntryRepository.class);
        receivables = mock(CustomerReceivableRepository.class);
        service = new ReceiptEmailService(orderRepository, customerRepository, emailPort, payments, refunds, cashback,
                receivables);
    }

    @Test
    void envia_para_o_cliente_vinculado_e_devolve_o_email_mascarado() {
        order(42L, false);
        pagoEmPix();
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));

        String sentTo = service.sendPurchaseReceipt(7L);

        assertThat(sentTo).isEqualTo("jo***@gmail.com");
        verify(emailPort).sendPurchaseReceipt(eq("joana@gmail.com"),
                argThat(v -> "#7".equals(v.orderReference()) && "Joana".equals(v.customerName())),
                argThat(r -> "#7".equals(r.reference())));
    }

    @Test
    void comprovante_traz_o_numero_do_cupom_mesa_pagamento_e_troco() {
        Order order = order(42L, false);
        when(order.orderNumber()).thenReturn("000123");
        when(order.tableLabel()).thenReturn("Mesa 5");
        when(order.changeAmount()).thenReturn(new BigDecimal("10.10"));
        when(order.concludedAt()).thenReturn(Instant.parse("2026-11-04T17:32:00Z"));
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));
        when(payments.findByOrderId(7L)).thenReturn(List.of(
                OrderPayment.captured(7L, PaymentMethod.DINHEIRO, new BigDecimal("60.00"), null),
                OrderPayment.captured(7L, PaymentMethod.CREDITO, new BigDecimal("80.00"), 3),
                OrderPayment.refunded(OrderPayment.captured(7L, PaymentMethod.PIX, new BigDecimal("5.00"), null))));

        PurchaseReceiptEmail receipt = enviar();

        assertThat(receipt.reference()).isEqualTo("nº 000123");
        assertThat(receipt.summary()).extracting(OrderStageEmail.Detail::value)
                .containsExactly("nº 000123", "04/11/2026 às 14:32", "5");
        assertThat(receipt.payments()).extracting(OrderStageEmail.Detail::label)
                .containsExactly("Dinheiro", "Cartão de crédito (3x)", "Troco");
        assertThat(receipt.preheader()).contains("04/11/2026 às 14:32").endsWith("Valeu pela compra!");
        assertThat(receipt.cashback()).isNull();
        assertThat(receipt.partiallyRefunded()).isFalse();
    }

    @Test
    void compra_marcada_mostra_o_vencimento() {
        order(42L, false);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));
        OrderPayment marcado = mock(OrderPayment.class);
        when(marcado.status()).thenReturn(PaymentStatus.ON_ACCOUNT);
        when(marcado.method()).thenReturn(PaymentMethod.MARCADO);
        when(marcado.amount()).thenReturn(new BigDecimal("129.90"));
        when(payments.findByOrderId(7L)).thenReturn(List.of(marcado));
        CustomerReceivable receivable = mock(CustomerReceivable.class);
        when(receivable.dueDate()).thenReturn(LocalDate.of(2026, 12, 4));
        when(receivables.findByOrderId(7L)).thenReturn(Optional.of(receivable));

        PurchaseReceiptEmail receipt = enviar();

        assertThat(receipt.payments()).extracting(OrderStageEmail.Detail::label)
                .containsExactly("Marcado (vence em 04/12/2026)");
        assertThat(receipt.preheader()).endsWith("Vence em 04/12/2026.");
    }

    @Test
    void cashback_em_carencia_diz_quando_fica_disponivel() {
        order(42L, false);
        pagoEmPix();
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));
        Instant disponivel = Instant.now().plus(7, ChronoUnit.DAYS);
        CashbackEntry ganho = mock(CashbackEntry.class);
        when(ganho.amount()).thenReturn(new BigDecimal("3.90"));
        when(ganho.availableAt()).thenReturn(disponivel);
        when(cashback.findEarnedByOrderId(7L)).thenReturn(List.of(ganho));

        assertThat(enviar().cashback()).contains("3,90")
                .contains(EmailFormat.date(LocalDate.ofInstant(disponivel, EmailFormat.ZONE)));
    }

    @Test
    void cashback_ja_liberado_nao_promete_data() {
        order(42L, false);
        pagoEmPix();
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));
        CashbackEntry ganho = mock(CashbackEntry.class);
        when(ganho.amount()).thenReturn(new BigDecimal("3.90"));
        when(ganho.availableAt()).thenReturn(Instant.now().minusSeconds(60));
        when(cashback.findEarnedByOrderId(7L)).thenReturn(List.of(ganho));

        assertThat(enviar().cashback()).contains("Já está disponível");
    }

    @Test
    void reembolso_parcial_avisa_mas_troca_nao() {
        order(42L, false);
        pagoEmPix();
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));
        OrderItemRefund troca = mock(OrderItemRefund.class);
        when(troca.exchangeId()).thenReturn(9L);
        when(refunds.findByOrderId(7L)).thenReturn(List.of(troca));
        assertThat(enviar().partiallyRefunded()).isFalse();

        reset(emailPort);
        OrderItemRefund estorno = mock(OrderItemRefund.class);
        // Sem isto o mock devolve 0L para um Long, e o estorno pareceria uma troca.
        when(estorno.exchangeId()).thenReturn(null);
        when(refunds.findByOrderId(7L)).thenReturn(List.of(estorno));
        assertThat(enviar().partiallyRefunded()).isTrue();
    }

    @Test
    void pedido_reembolsado_e_recusado() {
        Order order = order(42L, false);
        when(order.status()).thenReturn(OrderStatus.REEMBOLSADO);

        assertThatThrownBy(() -> service.sendPurchaseReceipt(7L))
                .isInstanceOf(ReceiptEmailUnavailableException.class)
                .hasMessageContaining("reembolsado");
        verifyNoInteractions(emailPort);
    }

    @Test
    void pedido_sem_pagamento_confirmado_e_recusado() {
        order(42L, false);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));

        assertThatThrownBy(() -> service.sendPurchaseReceipt(7L))
                .isInstanceOf(ReceiptEmailUnavailableException.class)
                .hasMessageContaining("sem pagamento");
        verifyNoInteractions(emailPort);
    }

    @Test
    void cortesia_sem_valor_a_pagar_ainda_tem_comprovante() {
        Order order = order(42L, false);
        when(order.totalPayable()).thenReturn(BigDecimal.ZERO);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));

        assertThat(enviar().hasPayments()).isFalse();
    }

    @Test
    void pedido_sem_cliente_e_recusado() {
        order(null, false);

        assertThatThrownBy(() -> service.sendPurchaseReceipt(7L))
                .isInstanceOf(ReceiptEmailUnavailableException.class)
                .hasMessageContaining("sem cliente");
        verifyNoInteractions(emailPort);
    }

    @Test
    void cliente_sem_email_e_recusado() {
        order(42L, false);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer(null)));

        assertThatThrownBy(() -> service.sendPurchaseReceipt(7L))
                .isInstanceOf(ReceiptEmailUnavailableException.class)
                .hasMessageContaining("sem e-mail");
    }

    @Test
    void pedido_cancelado_e_recusado() {
        order(42L, true);

        assertThatThrownBy(() -> service.sendPurchaseReceipt(7L))
                .isInstanceOf(ReceiptEmailUnavailableException.class)
                .hasMessageContaining("cancelado");
    }

    @Test
    void pedido_inexistente() {
        when(orderRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendPurchaseReceipt(7L)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void mascara_email_curto() {
        assertThat(ReceiptEmailService.mask("a@b.com")).isEqualTo("a***@b.com");
        assertThat(ReceiptEmailService.mask("invalido")).isEqualTo("***");
    }

    private PurchaseReceiptEmail enviar() {
        service.sendPurchaseReceipt(7L);
        ArgumentCaptor<PurchaseReceiptEmail> receipt = ArgumentCaptor.forClass(PurchaseReceiptEmail.class);
        verify(emailPort).sendPurchaseReceipt(any(), any(), receipt.capture());
        return receipt.getValue();
    }

    private void pagoEmPix() {
        when(payments.findByOrderId(7L)).thenReturn(List.of(
                OrderPayment.captured(7L, PaymentMethod.PIX, new BigDecimal("129.90"), null)));
    }

    private Order order(Long customerId, boolean cancelled) {
        Order order = mock(Order.class);
        when(order.id()).thenReturn(7L);
        when(order.customerId()).thenReturn(customerId);
        when(order.isCancelled()).thenReturn(cancelled);
        when(order.items()).thenReturn(List.of());
        when(order.totalPayable()).thenReturn(new BigDecimal("129.90"));
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        return order;
    }

    private static Customer customer(String email) {
        return new Customer(42L, "Joana", "11999999999", email, null, "PDV", Instant.now(), CustomerStage.NOVO_LEAD);
    }
}
