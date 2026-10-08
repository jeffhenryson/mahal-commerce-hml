package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.NotificationUseCase;
import com.cernecommerce.core.ports.in.ReceivableUseCase;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.NotificationSsePort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private static final Map<String, String> ORDER_STATUS_LABELS = Map.of(
            "PAGO", "Pagamento confirmado",
            "SEPARADO", "Pedido separado",
            "ENVIADO", "Pedido enviado",
            "ENTREGUE", "Pedido entregue",
            "CONCLUIDO", "Pedido concluído");

    private final NotificationUseCase notificationUseCase;
    private final OperationalEmailDispatcher preferences;
    private final UserRepository userRepository;
    private final EmailPort emailPort;
    private final NotificationSsePort ssePort;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ReceivableUseCase receivableUseCase;
    private final Executor executor;

    public NotificationEventListener(NotificationUseCase notificationUseCase,
                                     OperationalEmailDispatcher preferences,
                                     UserRepository userRepository,
                                     EmailPort emailPort,
                                     NotificationSsePort ssePort,
                                     OrderRepository orderRepository,
                                     CustomerRepository customerRepository,
                                     ReceivableUseCase receivableUseCase,
                                     @Qualifier("taskExecutor") Executor executor) {
        this.notificationUseCase = notificationUseCase;
        this.preferences = preferences;
        this.userRepository = userRepository;
        this.emailPort = emailPort;
        this.ssePort = ssePort;
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.receivableUseCase = receivableUseCase;
        this.executor = executor;
    }

    /**
     * Síncrono só para ler IP e user agent da requisição — que não existem mais na thread do
     * executor — e logo entrega o trabalho de verdade ao {@code taskExecutor}.
     */
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        SecurityEventContext context = requestContext(event);
        try {
            executor.execute(() -> {
                try {
                    handle(event, context);
                } catch (Exception ex) {
                    log.error("notification.event.failed type={} error={}", event.type(), ex.getMessage());
                }
            });
        } catch (Exception ex) {
            // Fila cheia não pode derrubar a requisição que publicou o evento.
            log.error("notification.event.rejected type={} error={}", event.type(), ex.getMessage());
        }
    }

    void handle(AuditEvent event, SecurityEventContext context) {
        switch (event.type()) {
            case USER_PASSWORD_CHANGED -> {
                dispatch(event.username(), NotificationType.PASSWORD_CHANGED,
                        "Senha alterada", "Sua senha foi alterada. Se não foi você, contate o suporte.",
                        to -> emailPort.sendPasswordChangedAlert(to, event.username(), context));
            }
            case ACCOUNT_LOCKED -> {
                dispatch(event.username(), NotificationType.ACCOUNT_LOCKED,
                        "Conta bloqueada", "Sua conta foi bloqueada por excesso de tentativas.",
                        to -> emailPort.sendAccountLockedAlert(to, event.username(), context));
            }
            case TOTP_ENABLED -> {
                dispatch(event.username(), NotificationType.TOTP_ENABLED,
                        "Autenticação 2FA ativada", "A verificação em duas etapas foi ativada na sua conta.",
                        to -> emailPort.sendTotpStatusAlert(to, event.username(), true, context));
            }
            case TOTP_DISABLED -> {
                dispatch(event.username(), NotificationType.TOTP_DISABLED,
                        "Autenticação 2FA desativada", "A verificação em duas etapas foi desativada na sua conta.",
                        to -> emailPort.sendTotpStatusAlert(to, event.username(), false, context));
            }
            case TOKEN_THEFT_DETECTED -> {
                dispatch(event.username(), NotificationType.TOKEN_THEFT_DETECTED,
                        "Atividade suspeita detectada", "Detectamos uso suspeito do seu token de acesso. Todas as sessões foram encerradas.",
                        to -> emailPort.sendTokenTheftAlert(to, event.username(), context));
            }
            case USER_EMAIL_CHANGED -> {
                dispatch(event.username(), NotificationType.EMAIL_CHANGED,
                        "Email alterado", "O endereço de email da sua conta foi alterado.", null);
            }
            // Lido depois da troca: o e-mail do usuário já é o novo, que é quem precisa da confirmação.
            case EMAIL_CHANGE_CONFIRMED -> dispatch(event.username(), NotificationType.EMAIL_CHANGED,
                    "Email alterado", "O endereço de email da sua conta foi alterado.",
                    to -> emailPort.sendEmailChangeConfirmed(to, event.username()));
            case USER_ROLE_ASSIGNED -> {
                String role = String.valueOf(event.details().get("role"));
                String body = "O papel " + role + " foi atribuído à sua conta.";
                dispatch(event.username(), NotificationType.ROLE_ASSIGNED, "Papel atribuído", body,
                        to -> emailPort.sendAccountChange(to, event.username(), "Papel atribuído", body));
            }
            case USER_ROLE_REMOVED -> {
                String role = String.valueOf(event.details().get("role"));
                String body = "O papel " + role + " foi removido da sua conta.";
                dispatch(event.username(), NotificationType.ROLE_REMOVED, "Papel removido", body,
                        to -> emailPort.sendAccountChange(to, event.username(), "Papel removido", body));
            }
            case USER_DISABLED -> {
                String body = "Sua conta foi desativada por um administrador.";
                dispatch(event.username(), NotificationType.ACCOUNT_DISABLED, "Conta desativada", body,
                        to -> emailPort.sendAccountChange(to, event.username(), "Conta desativada", body));
            }
            case PASSWORD_RESET_COMPLETED -> dispatch(event.username(), NotificationType.PASSWORD_CHANGED,
                    "Senha redefinida", "Sua senha foi redefinida pelo link de recuperação.",
                    to -> emailPort.sendPasswordResetAlert(to, event.username(), context));
            case OAUTH_GOOGLE_LINKED -> dispatch(event.username(), NotificationType.PASSWORD_CHANGED,
                    "Login com Google ativado", "Sua conta passou a aceitar login com Google.",
                    to -> emailPort.sendGoogleLinkedAlert(to, event.username(), context));
            case RECEIVABLE_CREATED -> sendReceivableCreated(event);
            case RECEIVABLE_PAID -> sendReceivablePaid(event);
            // Boas-vindas não tem preferência: sai uma vez só, quando a conta passa a existir de fato.
            // Usuário convidado já recebeu o convite (USER_INVITE) — o boas-vindas seria repetido.
            case USER_EMAIL_VERIFIED -> sendEmail(event.username(), to -> emailPort.sendWelcome(to, event.username()));
            case USER_CREATED -> {
                if (!Boolean.TRUE.equals(event.details().get("invited"))) {
                    sendEmail(event.username(), to -> emailPort.sendWelcome(to, event.username()));
                }
            }
            case ORDER_STATUS_CHANGED -> {
                String to = String.valueOf(event.details().get("to"));
                String label = ORDER_STATUS_LABELS.getOrDefault(to, "Status atualizado");
                resolveOrderCustomer(event).ifPresent(oc -> emailPort.sendOrderStatusUpdate(
                        oc.customer().email(), view(oc), label));
            }
            case ORDER_CANCELLED, ORDER_REFUNDED -> {
                boolean refunded = event.type() == AuditEvent.EventType.ORDER_REFUNDED;
                Object reason = event.details().get("reason");
                resolveOrderCustomer(event).ifPresent(oc -> emailPort.sendOrderCancellation(
                        oc.customer().email(), view(oc), reason == null ? null : reason.toString(), refunded));
            }
            default -> { }
        }
    }

    /**
     * Fiado (CRM-F010): comprovante da compra marcada, para o cliente saber quanto deve e até quando.
     * É aviso de cobrança, não marketing — não passa por preferência.
     */
    private void sendReceivableCreated(AuditEvent event) {
        try {
            Long orderId = ((Number) event.details().get("orderId")).longValue();
            receivableUseCase.findByOrderId(orderId).ifPresent(receivable ->
                    customerEmail(receivable.customerId()).ifPresent(customer -> {
                        BigDecimal open = receivable.amountOpen();
                        List<NotificationEmail.Row> items = receivable.items() == null ? List.of()
                                : receivable.items().stream()
                                        .map(i -> NotificationEmail.Row.of(itemLabel(i.productName(), i.sku(), i.quantity()),
                                                EmailFormat.money(i.subtotal())))
                                        .toList();
                        emailPort.sendCustomerNotice(customer.email(), NotificationEmail
                                .builder("fiado.criado", "Compra marcada: " + EmailFormat.money(open)
                                        + " com vencimento em " + EmailFormat.date(receivable.dueDate()))
                                .title("Sua compra foi marcada")
                                .intro("Olá, " + customer.nome() + "! Registramos sua compra para pagamento posterior.")
                                .tone(NotificationEmail.Tone.INFO)
                                .section("Itens", items)
                                .section("Pagamento", List.of(
                                        NotificationEmail.Row.highlighted("Valor marcado", EmailFormat.money(open)),
                                        NotificationEmail.Row.of("Vencimento", EmailFormat.date(receivable.dueDate()))))
                                .build());
                    }));
        } catch (Exception ex) {
            log.error("notification.receivable-created.failed details={} error={}", event.details(), ex.getMessage());
        }
    }

    private void sendReceivablePaid(AuditEvent event) {
        try {
            Long customerId = ((Number) event.details().get("customerId")).longValue();
            BigDecimal paid = BigDecimal.ZERO;
            if (event.details().get("applied") instanceof List<?> applied) {
                for (Object line : applied) {
                    if (line instanceof Map<?, ?> m) {
                        BigDecimal amount = EmailFormat.toBigDecimal(m.get("amount"));
                        paid = amount == null ? paid : paid.add(amount);
                    }
                }
            }
            BigDecimal paidTotal = paid;
            BigDecimal remaining = EmailFormat.toBigDecimal(event.details().get("openBalanceAfter"));
            customerEmail(customerId).ifPresent(customer -> emailPort.sendCustomerNotice(customer.email(),
                    NotificationEmail.builder("fiado.quitado", "Pagamento recebido: " + EmailFormat.money(paidTotal))
                            .title("Recebemos seu pagamento")
                            .intro("Olá, " + customer.nome() + "! Obrigado pelo pagamento.")
                            .tone(NotificationEmail.Tone.SUCCESS)
                            .section(null, List.of(
                                    NotificationEmail.Row.highlighted("Valor pago", EmailFormat.money(paidTotal)),
                                    NotificationEmail.Row.of("Saldo em aberto",
                                            remaining == null || remaining.signum() == 0 ? "Nada — tudo quitado"
                                                    : EmailFormat.money(remaining)),
                                    NotificationEmail.Row.of("Quando", EmailFormat.dateTime(event.timestamp()))))
                            .build()));
        } catch (Exception ex) {
            log.error("notification.receivable-paid.failed details={} error={}", event.details(), ex.getMessage());
        }
    }

    private Optional<Customer> customerEmail(Long customerId) {
        return customerRepository.findById(customerId)
                .filter(c -> c.email() != null && !c.email().isBlank());
    }

    static String itemLabel(String productName, String sku, BigDecimal quantity) {
        String name = productName != null && !productName.isBlank() ? productName : sku;
        String qty = quantity == null ? "" : quantity.stripTrailingZeros().toPlainString().replace('.', ',') + "× ";
        return qty + name;
    }

    private record OrderCustomer(Order order, Customer customer) { }

    private Optional<OrderCustomer> resolveOrderCustomer(AuditEvent event) {
        try {
            Long orderId = ((Number) event.details().get("orderId")).longValue();
            // Só marketplace: no balcão e na mesa o cliente está na loja, e o comprovante sai só
            // quando o operador pede (sendPurchaseReceipt).
            return orderRepository.findById(orderId)
                    .filter(order -> order.channel() == SalesChannel.MARKETPLACE)
                    .filter(order -> order.customerId() != null)
                    .flatMap(order -> customerRepository.findById(order.customerId())
                            .filter(customer -> customer.email() != null && !customer.email().isBlank())
                            .map(customer -> new OrderCustomer(order, customer)));
        } catch (Exception ex) {
            log.error("notification.order-email.resolve.failed details={} error={}", event.details(), ex.getMessage());
            return Optional.empty();
        }
    }

    private static OrderEmailView view(OrderCustomer oc) {
        return OrderEmailView.of(oc.order(), oc.customer().nome());
    }

    private void dispatch(String username, NotificationType type, String title, String body,
                          Consumer<String> emailAction) {
        NotificationPreference pref = preferences.preference(username, type);
        if (pref.inAppEnabled()) {
            persist(username, type, title, body);
        }
        if (emailAction != null && pref.emailEnabled()) {
            sendEmail(username, emailAction);
        }
    }

    private void persist(String username, NotificationType type, String title, String body) {
        try {
            Notification saved = notificationUseCase.notify(username, type, title, body);
            ssePort.send(username, saved);
        } catch (Exception ex) {
            log.error("notification.persist.failed username={} type={} error={}", username, type, ex.getMessage());
        }
    }

    private void sendEmail(String username, Consumer<String> sendAction) {
        try {
            userRepository.findByUsername(username)
                    .map(u -> u.getEmail())
                    .filter(email -> email != null && !email.isBlank())
                    .ifPresent(sendAction);
        } catch (Exception ex) {
            log.error("notification.email.failed username={} error={}", username, ex.getMessage());
        }
    }

    private static SecurityEventContext requestContext(AuditEvent event) {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
                var request = sra.getRequest();
                return new SecurityEventContext(event.timestamp(), request.getRemoteAddr(),
                        request.getHeader("User-Agent"));
            }
        } catch (Exception ignored) {
            // Fora de requisição (scheduler, executor): segue só com o horário.
        }
        return SecurityEventContext.at(event.timestamp());
    }
}
