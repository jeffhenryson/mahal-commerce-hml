package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.AvisoClientePedidoAppUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.out.AfterCommitExecutor;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Avisa o cliente de cada etapa do pedido do app (ECM-F010, ECM-F013): e-mail, se ele tem, e
 * notificação no app (frontend-market), com o empurrão por SSE se ele estiver com o app aberto.
 *
 * <p>Sempre <b>depois do commit</b> — etapa revertida não avisa ninguém — e cada canal falha sozinho:
 * e-mail fora do ar não impede a notificação, nem o contrário. A etapa já está gravada; o
 * {@code GET /shop/orders/{id}} a mostra de qualquer jeito.</p>
 */
public class AvisoClientePedidoApp implements AvisoClientePedidoAppUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvisoClientePedidoApp.class);

    private final CrmUseCase crmUseCase;
    private final UserRepository userRepository;
    private final NotificationUseCase notificationUseCase;
    private final NotificationSsePort ssePort;
    private final EmailPort emailPort;
    private final AfterCommitExecutor afterCommit;

    public AvisoClientePedidoApp(CrmUseCase crmUseCase, UserRepository userRepository,
            NotificationUseCase notificationUseCase, NotificationSsePort ssePort, EmailPort emailPort,
            AfterCommitExecutor afterCommit) {
        this.crmUseCase = crmUseCase;
        this.userRepository = userRepository;
        this.notificationUseCase = notificationUseCase;
        this.ssePort = ssePort;
        this.emailPort = emailPort;
        this.afterCommit = afterCommit;
    }

    /** Etapa nova — {@code mensagem} vem de {@code MensagemCliente}. */
    @Override
    public void etapa(Order order, String mensagem) {
        Customer cliente = cliente(order);
        if (cliente == null) {
            return;
        }
        OrderEmailView view = OrderEmailView.of(order, cliente.nome());
        afterCommit.accumulate("shop.pedido-app.etapa:" + order.id() + ":" + mensagem, order, pedidos -> {
            if (temEmail(cliente)) {
                try {
                    emailPort.sendOrderStatusUpdate(cliente.email(), view, mensagem);
                } catch (RuntimeException e) {
                    log.warn("E-mail da etapa do pedido {} não enviado: {}", order.id(), e.getMessage(), e);
                }
            }
            notificar(order, mensagem);
        });
    }

    /**
     * Só a notificação no app, sem e-mail — para quando o e-mail sai por outro caminho (a recusa, que
     * o {@code ORDER_CANCELLED} do controller já manda por e-mail).
     */
    @Override
    public void soNotificacao(Order order, String mensagem) {
        if (order.customerId() == null) {
            return;
        }
        afterCommit.accumulate("shop.pedido-app.notificacao:" + order.id() + ":" + mensagem, order,
                pedidos -> notificar(order, mensagem));
    }

    /** Cancelado pela loja ou expirado — e-mail de cancelamento e notificação. */
    @Override
    public void cancelamento(Order order, String motivo) {
        Customer cliente = cliente(order);
        if (cliente == null) {
            return;
        }
        OrderEmailView view = OrderEmailView.of(order, cliente.nome());
        afterCommit.accumulate("shop.pedido-app.cancelado:" + order.id(), order, pedidos -> {
            if (temEmail(cliente)) {
                try {
                    emailPort.sendOrderCancellation(cliente.email(), view, motivo, false);
                } catch (RuntimeException e) {
                    log.warn("E-mail de cancelamento do pedido {} não enviado: {}", order.id(), e.getMessage(), e);
                }
            }
            notificar(order, "Pedido cancelado" + (motivo == null || motivo.isBlank() ? "" : ": " + motivo));
        });
    }

    private void notificar(Order order, String mensagem) {
        try {
            userRepository.findByCustomerId(order.customerId()).map(User::getUsername).ifPresent(username -> {
                Notification salva = notificationUseCase.notify(username, NotificationType.PEDIDO_CLIENTE,
                        "Pedido #" + order.id(), mensagem, "/pedidos/" + order.id());
                ssePort.send(username, salva);
            });
        } catch (RuntimeException e) {
            log.warn("Notificação da etapa do pedido {} não enviada: {}", order.id(), e.getMessage(), e);
        }
    }

    private Customer cliente(Order order) {
        if (order.customerId() == null) {
            return null;
        }
        return crmUseCase.findCustomersByIds(Set.of(order.customerId())).get(order.customerId());
    }

    private static boolean temEmail(Customer cliente) {
        return cliente.email() != null && !cliente.email().isBlank();
    }
}
