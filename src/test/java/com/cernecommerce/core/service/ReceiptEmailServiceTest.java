package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ReceiptEmailUnavailableException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptEmailServiceTest {

    private OrderRepository orderRepository;
    private CustomerRepository customerRepository;
    private EmailPort emailPort;
    private ReceiptEmailService service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        customerRepository = mock(CustomerRepository.class);
        emailPort = mock(EmailPort.class);
        service = new ReceiptEmailService(orderRepository, customerRepository, emailPort);
    }

    @Test
    void envia_para_o_cliente_vinculado_e_devolve_o_email_mascarado() {
        order(42L, false);
        when(customerRepository.findById(42L)).thenReturn(Optional.of(customer("joana@gmail.com")));

        String sentTo = service.sendPurchaseReceipt(7L);

        assertThat(sentTo).isEqualTo("jo***@gmail.com");
        verify(emailPort).sendPurchaseReceipt(eq("joana@gmail.com"),
                argThat(v -> "#7".equals(v.orderReference()) && "Joana".equals(v.customerName())));
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

    private void order(Long customerId, boolean cancelled) {
        Order order = mock(Order.class);
        when(order.id()).thenReturn(7L);
        when(order.customerId()).thenReturn(customerId);
        when(order.isCancelled()).thenReturn(cancelled);
        when(order.items()).thenReturn(List.of());
        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
    }

    private static Customer customer(String email) {
        return new Customer(42L, "Joana", "11999999999", email, null, "PDV", Instant.now(), CustomerStage.NOVO_LEAD);
    }
}
