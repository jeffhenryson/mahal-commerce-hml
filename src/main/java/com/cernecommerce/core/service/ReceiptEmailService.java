package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.ReceiptEmailUnavailableException;
import com.cernecommerce.core.domain.exception.pedido.OrderNotFoundException;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail.Detail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.ports.in.ReceiptEmailUseCase;
import com.cernecommerce.core.ports.out.cashback.CashbackEntryRepository;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import com.cernecommerce.core.ports.out.pedido.OrderItemRefundRepository;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.recebivel.CustomerReceivableRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Comprovante por e-mail da compra presencial. Só para o cliente vinculado ao pedido: mandar para
 * um endereço digitado no balcão abriria o PDV como disparador de e-mail para qualquer um. Acompanha
 * o cupom impresso: mesmo número, formas de pagamento e troco.
 */
public class ReceiptEmailService implements ReceiptEmailUseCase {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final EmailPort emailPort;
    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderItemRefundRepository itemRefundRepository;
    private final CashbackEntryRepository cashbackEntryRepository;
    private final CustomerReceivableRepository receivableRepository;

    public ReceiptEmailService(OrderRepository orderRepository, CustomerRepository customerRepository,
            EmailPort emailPort, OrderPaymentRepository orderPaymentRepository,
            OrderItemRefundRepository itemRefundRepository, CashbackEntryRepository cashbackEntryRepository,
            CustomerReceivableRepository receivableRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.emailPort = emailPort;
        this.orderPaymentRepository = orderPaymentRepository;
        this.itemRefundRepository = itemRefundRepository;
        this.cashbackEntryRepository = cashbackEntryRepository;
        this.receivableRepository = receivableRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public String sendPurchaseReceipt(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.isCancelled()) {
            throw new ReceiptEmailUnavailableException(orderId, "pedido cancelado");
        }
        if (order.status() == OrderStatus.REEMBOLSADO) {
            throw new ReceiptEmailUnavailableException(orderId, "pedido reembolsado");
        }
        if (order.customerId() == null) {
            throw new ReceiptEmailUnavailableException(orderId, "pedido sem cliente vinculado");
        }
        Customer customer = customerRepository.findById(order.customerId())
                .orElseThrow(() -> new ReceiptEmailUnavailableException(orderId, "cliente não encontrado"));
        if (customer.email() == null || customer.email().isBlank()) {
            throw new ReceiptEmailUnavailableException(orderId, "cliente sem e-mail cadastrado");
        }
        List<OrderPayment> payments = orderPaymentRepository.findByOrderId(orderId).stream()
                .filter(p -> p.status() == PaymentStatus.CAPTURED || p.status() == PaymentStatus.ON_ACCOUNT)
                .toList();
        // Total zerado (cortesia) não tem linha de pagamento, e a compra aconteceu do mesmo jeito.
        if (payments.isEmpty() && order.totalPayable().signum() > 0) {
            throw new ReceiptEmailUnavailableException(orderId, "pedido sem pagamento confirmado");
        }
        emailPort.sendPurchaseReceipt(customer.email(), OrderEmailView.of(order, customer.nome()),
                receipt(order, payments));
        return mask(customer.email());
    }

    private PurchaseReceiptEmail receipt(Order order, List<OrderPayment> payments) {
        String reference = order.orderNumber() != null ? "nº " + order.orderNumber() : "#" + order.id();
        String date = EmailFormat.dateTime(purchasedAt(order));
        LocalDate dueDate = payments.stream().anyMatch(p -> p.method() == PaymentMethod.MARCADO)
                ? receivableRepository.findByOrderId(order.id()).map(CustomerReceivable::dueDate).orElse(null)
                : null;

        List<Detail> summary = new ArrayList<>(List.of(new Detail("Compra:", reference), new Detail("Data:", date)));
        if (order.tableLabel() != null) {
            // O rótulo já vem como "Mesa 4".
            summary.add(new Detail("Mesa:", order.tableLabel().replaceFirst("(?i)^mesa\\s+", "")));
        }
        List<Detail> lines = new ArrayList<>(payments.stream()
                .map(p -> new Detail(paymentLabel(p, dueDate), EmailFormat.money(p.amount())))
                .toList());
        if (order.changeAmount() != null && order.changeAmount().signum() > 0) {
            lines.add(new Detail("Troco", EmailFormat.money(order.changeAmount())));
        }
        String preheader = EmailFormat.money(order.totalPayable()) + " em " + date + ". "
                + (dueDate == null ? "Valeu pela compra!" : "Vence em " + EmailFormat.date(dueDate) + ".");
        boolean partiallyRefunded = itemRefundRepository.findByOrderId(order.id()).stream()
                .anyMatch(refund -> refund.exchangeId() == null);
        return new PurchaseReceiptEmail(reference, preheader, summary, lines, cashback(order.id()), partiallyRefunded);
    }

    private static Instant purchasedAt(Order order) {
        if (order.concludedAt() != null) {
            return order.concludedAt();
        }
        return order.paidAt() != null ? order.paidAt() : order.createdAt();
    }

    private static String paymentLabel(OrderPayment payment, LocalDate dueDate) {
        return switch (payment.method()) {
            case DINHEIRO -> "Dinheiro";
            case DEBITO -> "Cartão de débito";
            case CREDITO -> payment.installments() != null && payment.installments() > 1
                    ? "Cartão de crédito (" + payment.installments() + "x)" : "Cartão de crédito";
            case PIX -> "Pix";
            case GATEWAY_PIX -> "Pix online";
            case MARCADO -> dueDate == null ? "Marcado" : "Marcado (vence em " + EmailFormat.date(dueDate) + ")";
        };
    }

    private String cashback(Long orderId) {
        List<CashbackEntry> earned = cashbackEntryRepository.findEarnedByOrderId(orderId);
        BigDecimal amount = earned.stream().map(CashbackEntry::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.signum() <= 0) {
            return null;
        }
        Instant availableAt = earned.stream().map(CashbackEntry::availableAt).max(Comparator.naturalOrder()).orElseThrow();
        String ganho = "você ganhou " + EmailFormat.money(amount) + " nesta compra. ";
        return availableAt.isAfter(Instant.now())
                ? ganho + "Fica disponível a partir de "
                        + EmailFormat.date(LocalDate.ofInstant(availableAt, EmailFormat.ZONE)) + "."
                : ganho + "Já está disponível no seu saldo.";
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
