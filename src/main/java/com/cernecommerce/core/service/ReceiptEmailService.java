package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ReceiptEmailUnavailableException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ReceiptEmailUseCase;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comprovante por e-mail da compra presencial. Só para o cliente vinculado ao pedido: mandar para
 * um endereço digitado no balcão abriria o PDV como disparador de e-mail para qualquer um.
 */
public class ReceiptEmailService implements ReceiptEmailUseCase {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final EmailPort emailPort;

    public ReceiptEmailService(OrderRepository orderRepository, CustomerRepository customerRepository,
            EmailPort emailPort) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.emailPort = emailPort;
    }

    @Override
    @Transactional(readOnly = true)
    public String sendPurchaseReceipt(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.isCancelled()) {
            throw new ReceiptEmailUnavailableException(orderId, "pedido cancelado");
        }
        if (order.customerId() == null) {
            throw new ReceiptEmailUnavailableException(orderId, "pedido sem cliente vinculado");
        }
        Customer customer = customerRepository.findById(order.customerId())
                .orElseThrow(() -> new ReceiptEmailUnavailableException(orderId, "cliente não encontrado"));
        if (customer.email() == null || customer.email().isBlank()) {
            throw new ReceiptEmailUnavailableException(orderId, "cliente sem e-mail cadastrado");
        }
        emailPort.sendPurchaseReceipt(customer.email(), OrderEmailView.of(order, customer.nome()));
        return mask(customer.email());
    }

    /** {@code joana@gmail.com} → {@code jo***@gmail.com}. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        return local.substring(0, Math.min(2, local.length())) + "***" + email.substring(at);
    }
}
