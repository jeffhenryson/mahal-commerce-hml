package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.recebivel.OnAccountNotAllowedException;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CRM-F010 — a borda do "Marcar", comum ao balcão e à mesa: a permissão do operador e o evento de
 * auditoria. As regras do cliente (VIP, limite, vencido) moram em {@code ReceivableService}.
 */
final class OnAccountGuard {

    static final String ON_ACCOUNT_AUTHORITY = "PDV_SALE_ON_ACCOUNT";

    private OnAccountGuard() {
    }

    static boolean mayMark(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ON_ACCOUNT_AUTHORITY::equals);
    }

    /** 403 ON_ACCOUNT_NOT_ALLOWED se a venda tem linha MARCADO e o operador não pode marcar. */
    static void requireAuthorityIfOnAccount(List<PaymentCommand> payments, Authentication authentication) {
        if (payments.stream().anyMatch(PaymentCommand::isOnAccount) && !mayMark(authentication)) {
            throw new OnAccountNotAllowedException();
        }
    }

    static void publishCreatedIfOnAccount(ApplicationEventPublisher publisher, Order order,
            List<PaymentCommand> payments, String username) {
        payments.stream().filter(PaymentCommand::isOnAccount).findFirst().ifPresent(line -> {
            Map<String, Object> details = new HashMap<>();
            details.put("orderId", order.id());
            details.put("orderNumber", order.orderNumber());
            details.put("customerId", order.customerId());
            details.put("amount", line.amount());
            details.put("dueDate", String.valueOf(line.dueDate()));
            details.values().removeIf(java.util.Objects::isNull);
            publisher.publishEvent(AuditEvent.of(EventType.RECEIVABLE_CREATED, username, details));
        });
    }
}
