package com.cernecommerce.infra.notification;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.rbac.RoleLabels;
import com.cernecommerce.core.domain.model.ecommerce.EmailEncerramentoPedido;
import com.cernecommerce.core.domain.model.ecommerce.EmailEtapaPedido;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.NotificationPreference;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.recebivel.CustomerReceivable;
import com.cernecommerce.core.domain.model.recebivel.ReceivableEmailText;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

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
                        "Senha alterada", "Sua senha foi alterada. Se não foi você, crie uma nova senha agora.",
                        to -> emailPort.sendPasswordChangedAlert(to, event.username(), context));
            }
            case ACCOUNT_LOCKED -> {
                dispatch(event.username(), NotificationType.ACCOUNT_LOCKED,
                        "Conta bloqueada", "Sua conta foi bloqueada por excesso de tentativas.",
                        to -> emailPort.sendAccountLockedAlert(to, greetingName(event.username()), alertContext(event.username(), context)));
            }
            case TOTP_ENABLED -> {
                dispatch(event.username(), NotificationType.TOTP_ENABLED,
                        "Verificação em duas etapas ativada", "A verificação em duas etapas foi ativada na sua conta.",
                        to -> emailPort.sendTotpStatusAlert(to, event.username(), true, context));
            }
            case TOTP_DISABLED -> {
                dispatch(event.username(), NotificationType.TOTP_DISABLED,
                        "Verificação em duas etapas desativada", "A verificação em duas etapas foi desativada na sua conta.",
                        to -> emailPort.sendTotpStatusAlert(to, event.username(), false, context));
            }
            case TOKEN_THEFT_DETECTED -> {
                dispatch(event.username(), NotificationType.TOKEN_THEFT_DETECTED,
                        "Conta desconectada por segurança", "Notamos um uso estranho do acesso à sua conta e desconectamos todos os aparelhos.",
                        to -> emailPort.sendTokenTheftAlert(to, greetingName(event.username()), alertContext(event.username(), context)));
            }
            case USER_EMAIL_CHANGED -> {
                dispatch(event.username(), NotificationType.EMAIL_CHANGED,
                        "E-mail alterado", "O endereço de e-mail da sua conta foi alterado.", null);
            }
            // Lido depois da troca: o e-mail do usuário já é o novo, que é quem precisa da confirmação.
            case EMAIL_CHANGE_CONFIRMED -> dispatch(event.username(), NotificationType.EMAIL_CHANGED,
                    "E-mail alterado", "O endereço de e-mail da sua conta foi alterado.",
                    to -> emailPort.sendEmailChangeConfirmed(to, event.username()));
            case USER_ROLE_ASSIGNED -> {
                String role = RoleLabels.of(String.valueOf(event.details().get("role")));
                String body = "O papel " + role + " foi atribuído à sua conta.";
                dispatch(event.username(), NotificationType.ROLE_ASSIGNED, "Papel atribuído", body,
                        to -> emailPort.sendAccountChange(to, event.username(), AccountChange.ROLE_ASSIGNED, role));
            }
            case USER_ROLE_REMOVED -> {
                String role = RoleLabels.of(String.valueOf(event.details().get("role")));
                String body = "O papel " + role + " foi removido da sua conta.";
                dispatch(event.username(), NotificationType.ROLE_REMOVED, "Papel removido", body,
                        to -> emailPort.sendAccountChange(to, event.username(), AccountChange.ROLE_REMOVED, role));
            }
            case USER_DISABLED -> {
                String body = "Sua conta foi desativada pela loja.";
                dispatch(event.username(), NotificationType.ACCOUNT_DISABLED, "Conta desativada", body,
                        to -> emailPort.sendAccountChange(to, greetingName(event.username()), AccountChange.DISABLED, null));
            }
            case PASSWORD_RESET_COMPLETED -> dispatch(event.username(), NotificationType.PASSWORD_CHANGED,
                    "Senha redefinida", "Sua senha foi redefinida pelo link de recuperação.",
                    to -> emailPort.sendPasswordResetAlert(to, greetingName(event.username()), alertContext(event.username(), context)));
            case OAUTH_GOOGLE_LINKED -> dispatch(event.username(), NotificationType.PASSWORD_CHANGED,
                    "Login com Google ativado", "Sua conta passou a aceitar login com Google.",
                    to -> emailPort.sendGoogleLinkedAlert(to, event.username(), context));
            case RECEIVABLE_CREATED -> sendReceivableCreated(event);
            case RECEIVABLE_PAID -> sendReceivablePaid(event);
            // Boas-vindas não tem preferência: sai uma vez só, quando a conta passa a existir de fato.
            // Usuário convidado já recebeu o convite (USER_INVITE) — o boas-vindas seria repetido.
            // Verificar o e-mail não conta: a pessoa já usa a conta e a página confirma o resultado.
            case USER_CREATED -> {
                if (!Boolean.TRUE.equals(event.details().get("invited"))) {
                    sendEmail(event.username(), to -> emailPort.sendWelcome(to, event.username()));
                }
            }
            case CUSTOMER_MARKETPLACE_REGISTERED -> sendCustomerWelcome(event);
            case ORDER_STATUS_CHANGED -> {
                // Sem "to" é o aceite do pedido do app, que já avisa o cliente pelo AvisoClientePedidoApp.
                Object to = event.details().get("to");
                if (to != null) {
                    resolveOrderCustomer(event).ifPresent(oc -> emailPort.sendOrderStatusUpdate(
                            oc.customer().email(), view(oc), EmailEtapaPedido.paraStatus(oc.order(), to.toString())));
                }
            }
            case ORDER_CANCELLED -> resolveOrderCustomer(event).ifPresent(oc -> emailPort.sendOrderCancellation(
                    oc.customer().email(), view(oc), cancellationEmail(event, view(oc))));
            case ORDER_REFUNDED -> resolveOrderCustomer(event).ifPresent(oc -> sendRefund(event, oc));
            default -> { }
        }
    }

    private static OrderClosureEmail cancellationEmail(AuditEvent event, OrderEmailView order) {
        Object origin = event.details().get("origin");
        if ("PEDIDO_APP_RECUSADO".equals(origin)) {
            Object reason = event.details().get("reason");
            return EmailEncerramentoPedido.recusado(order, reason == null ? null : reason.toString());
        }
        return "CLIENTE".equals(origin) ? EmailEncerramentoPedido.peloCliente(order)
                : EmailEncerramentoPedido.pelaLoja(order);
    }

    private void sendRefund(AuditEvent event, OrderCustomer oc) {
        Map<String, Object> details = event.details();
        boolean partial = Boolean.TRUE.equals(details.get("partial"));
        OrderEmailView order = view(oc);
        List<OrderEmailView.Line> lines = refundedLines(details.get("refundedItems"), oc.order());
        OrderEmailView shown = partial && !lines.isEmpty() ? order.withOnlyItems(lines) : order;
        BigDecimal itemsAmount = lines.stream().map(OrderEmailView.Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        emailPort.sendOrderCancellation(oc.customer().email(), shown, EmailEncerramentoPedido.reembolso(order,
                EmailFormat.toBigDecimal(details.get("moneyRefunded")), itemsAmount, partial,
                Boolean.TRUE.equals(details.get("onAccount"))));
    }

    private static List<OrderEmailView.Line> refundedLines(Object refundedItems, Order order) {
        if (!(refundedItems instanceof List<?> refunds)) {
            return List.of();
        }
        List<OrderEmailView.Line> lines = new ArrayList<>();
        for (Object refund : refunds) {
            if (refund instanceof Map<?, ?> m) {
                order.items().stream()
                        .filter(item -> Objects.equals(item.id(), m.get("orderItemId")))
                        .findFirst()
                        .ifPresent(item -> lines.add(OrderEmailView.refundLine(item,
                                EmailFormat.toBigDecimal(m.get("quantity")), EmailFormat.toBigDecimal(m.get("amount")))));
            }
        }
        return lines;
    }

    /**
     * Fiado (CRM-F010): comprovante da compra marcada, para o cliente saber quanto deve e até quando.
     * É aviso de cobrança, não marketing — não passa por preferência.
     */
    private void sendReceivableCreated(AuditEvent event) {
        try {
            Long orderId = ((Number) event.details().get("orderId")).longValue();
            receivableUseCase.findByOrderId(orderId).ifPresent(receivable ->
                    customerEmail(receivable.customerId()).ifPresent(customer -> emailPort.sendCustomerNotice(
                            customer.email(),
                            receivableCreatedEmail(receivable, customer, orderRepository.findById(orderId).orElse(null)))));
        } catch (Exception ex) {
            log.error("notification.receivable-created.failed details={} error={}", event.details(), ex.getMessage());
        }
    }

    private static NotificationEmail receivableCreatedEmail(CustomerReceivable receivable, Customer customer, Order order) {
        BigDecimal open = receivable.amountOpen();
        String amount = EmailFormat.money(open);
        String due = EmailFormat.date(receivable.dueDate());
        String purchased = EmailFormat.date(receivable.createdAt().atZone(EmailFormat.ZONE).toLocalDate());

        List<NotificationEmail.Row> payment = new ArrayList<>();
        // Os itens são do pedido inteiro: sem estas linhas, eles somam mais que o valor marcado.
        if (order != null && order.totalPayable().compareTo(open) > 0) {
            payment.add(NotificationEmail.Row.of("Total da compra", EmailFormat.money(order.totalPayable())));
            payment.add(NotificationEmail.Row.of("Pago na hora", EmailFormat.money(order.totalPayable().subtract(open))));
        }
        payment.add(NotificationEmail.Row.highlighted("Valor marcado", amount));
        payment.add(NotificationEmail.Row.of("Vencimento", due));
        if (order != null && order.orderNumber() != null) {
            payment.add(NotificationEmail.Row.of("Cupom", "nº " + order.orderNumber()));
        }
        List<NotificationEmail.Row> items = receivable.items() == null ? List.of()
                : receivable.items().stream()
                        .map(i -> NotificationEmail.Row.of(itemLabel(i.productName(), i.sku(), i.quantity()),
                                EmailFormat.money(i.subtotal())))
                        .toList();

        return NotificationEmail.builder("fiado.criado", "Compra marcada: " + amount + " até " + due)
                .title("Sua compra foi marcada")
                .intro("Olá, " + customer.firstName() + "! Registramos sua compra de " + purchased
                        + " para pagamento até " + due + ".")
                .preheader(ReceivableEmailText.PREHEADER)
                .tone(NotificationEmail.Tone.INFO)
                .section("Pagamento", payment)
                .text(ReceivableEmailText.HOW_TO_PAY)
                .section("Itens", items)
                .whatsapp(ReceivableEmailText.ACTION, ReceivableEmailText.whatsappMessage(amount))
                .footnote("Não reconhece esta compra? Fale com a loja.")
                .build();
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
                    receivablePaidEmail(customer, paidTotal, remaining, event)));
        } catch (Exception ex) {
            log.error("notification.receivable-paid.failed details={} error={}", event.details(), ex.getMessage());
        }
    }

    private NotificationEmail receivablePaidEmail(Customer customer, BigDecimal paid, BigDecimal remaining, AuditEvent event) {
        boolean settled = remaining == null || remaining.signum() == 0;
        List<NotificationEmail.Row> rows = new ArrayList<>();
        rows.add(NotificationEmail.Row.highlighted("Valor pago", EmailFormat.money(paid)));
        rows.add(NotificationEmail.Row.of("Saldo em aberto", settled ? "Nenhum" : EmailFormat.money(remaining)));
        if (!settled) {
            nextDueRow(customer.id()).ifPresent(rows::add);
        }
        rows.add(NotificationEmail.Row.of("Quando", EmailFormat.dateTime(event.timestamp())));

        NotificationEmail.Builder email = NotificationEmail
                .builder("fiado.quitado", "Pagamento recebido: " + EmailFormat.money(paid))
                .title("Recebemos seu pagamento")
                .tone(NotificationEmail.Tone.SUCCESS)
                .section(null, rows)
                .footnote("Não reconhece este pagamento? Fale com a loja.");
        if (settled) {
            return email.intro("Olá, " + customer.firstName() + "! Obrigado. Seu marcado está quitado.")
                    .preheader("Seu marcado está quitado.")
                    .build();
        }
        String balance = EmailFormat.money(remaining);
        return email.intro("Olá, " + customer.firstName() + "! Obrigado pelo pagamento. Ainda ficou um saldo em aberto.")
                .preheader("Ainda ficou " + balance + " em aberto.")
                .text(ReceivableEmailText.HOW_TO_PAY)
                .whatsapp(ReceivableEmailText.ACTION, ReceivableEmailText.whatsappMessage(balance))
                .build();
    }

    /** O vencimento mais antigo do que continua em aberto. */
    private Optional<NotificationEmail.Row> nextDueRow(Long customerId) {
        return receivableUseCase.listByCustomer(customerId).stream()
                .map(view -> view.receivable())
                .filter(r -> r.status().isOpen())
                .map(CustomerReceivable::dueDate)
                .min(Comparator.naturalOrder())
                .map(due -> NotificationEmail.Row.of(
                        due.isBefore(LocalDate.now(EmailFormat.ZONE)) ? "Vencido desde" : "Próximo vencimento",
                        EmailFormat.date(due)));
    }

    /** O username do cliente do app é o e-mail, que não serve de saudação. */
    private String greetingName(String username) {
        return userRepository.findByUsername(username)
                .filter(u -> u.isCustomer())
                .flatMap(u -> customerRepository.findById(u.getCustomerId()))
                .map(Customer::firstName)
                .orElse(username);
    }

    private SecurityEventContext alertContext(String username, SecurityEventContext context) {
        boolean customer = userRepository.findByUsername(username).filter(u -> u.isCustomer()).isPresent();
        return customer ? context.forCustomer() : context;
    }

    private void sendCustomerWelcome(AuditEvent event) {
        customerEmail(((Number) event.details().get("customerId")).longValue())
                .ifPresent(c -> emailPort.sendCustomerWelcome(c.email(), c.firstName()));
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
