package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSenderConfig;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.ports.in.EmailIntegrationUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * O {@link EmailPort} que o sistema injeta. A cada envio decide o provedor:
 * <ul>
 *   <li>integração de e-mail ativa em Dados da loja &gt; Integrações → Resend com a chave da loja,
 *       em qualquer ambiente;</li>
 *   <li>senão → o adapter escolhido por {@code email.provider} (logging, Mailpit ou Resend do env).</li>
 * </ul>
 *
 * <p>O adapter da loja é criado à mão (sem proxy de {@code @Async}), por isso o envio vai para o
 * {@code emailTaskExecutor} aqui — mesmo comportamento assíncrono do Resend do env. O fallback é
 * chamado direto: ele mesmo é assíncrono ou não, como antes.</p>
 */
public class RoutingEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(RoutingEmailAdapter.class);

    private final EmailPort fallback;
    private final EmailIntegrationUseCase integration;
    private final StoreResendAdapterFactory storeAdapterFactory;
    private final Executor emailExecutor;

    RoutingEmailAdapter(EmailPort fallback, EmailIntegrationUseCase integration,
            StoreResendAdapterFactory storeAdapterFactory, Executor emailExecutor) {
        this.fallback = fallback;
        this.integration = integration;
        this.storeAdapterFactory = storeAdapterFactory;
        this.emailExecutor = emailExecutor;
    }

    @Override
    public void sendVerificationCode(String to, String username, String code) {
        route(p -> p.sendVerificationCode(to, username, code));
    }

    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink, long ttlMinutes) {
        route(p -> p.sendPasswordResetLink(to, username, resetLink, ttlMinutes));
    }

    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        route(p -> p.sendEmailChangeNotification(oldEmail, username, newEmail));
    }

    @Override
    public void sendEmailChangeConfirmed(String newEmail, String username) {
        route(p -> p.sendEmailChangeConfirmed(newEmail, username));
    }

    @Override
    public void sendAccountChange(String to, String username, String title, String message) {
        route(p -> p.sendAccountChange(to, username, title, message));
    }

    @Override
    public void sendPasswordResetAlert(String to, String username, SecurityEventContext context) {
        route(p -> p.sendPasswordResetAlert(to, username, context));
    }

    @Override
    public void sendGoogleLinkedAlert(String to, String username, SecurityEventContext context) {
        route(p -> p.sendGoogleLinkedAlert(to, username, context));
    }

    @Override
    public void sendCustomerNotice(String to, NotificationEmail email) {
        route(p -> p.sendCustomerNotice(to, email));
    }

    @Override
    public void sendWelcome(String to, String username) {
        route(p -> p.sendWelcome(to, username));
    }

    @Override
    public void sendPasswordChangedAlert(String to, String username, SecurityEventContext context) {
        route(p -> p.sendPasswordChangedAlert(to, username, context));
    }

    @Override
    public void sendAccountLockedAlert(String to, String username, SecurityEventContext context) {
        route(p -> p.sendAccountLockedAlert(to, username, context));
    }

    @Override
    public void sendTotpStatusAlert(String to, String username, boolean enabled, SecurityEventContext context) {
        route(p -> p.sendTotpStatusAlert(to, username, enabled, context));
    }

    @Override
    public void sendTokenTheftAlert(String to, String username, SecurityEventContext context) {
        route(p -> p.sendTokenTheftAlert(to, username, context));
    }

    @Override
    public void sendOrderConfirmation(String to, OrderEmailView order, String checkoutUrl) {
        route(p -> p.sendOrderConfirmation(to, order, checkoutUrl));
    }

    @Override
    public void sendOrderStatusUpdate(String to, OrderEmailView order, String newStatusLabel) {
        route(p -> p.sendOrderStatusUpdate(to, order, newStatusLabel));
    }

    @Override
    public void sendOrderCancellation(String to, OrderEmailView order, String reason, boolean refunded) {
        route(p -> p.sendOrderCancellation(to, order, reason, refunded));
    }

    @Override
    public void sendPurchaseReceipt(String to, OrderEmailView order) {
        route(p -> p.sendPurchaseReceipt(to, order));
    }

    @Override
    public void sendNotification(String to, NotificationEmail email) {
        route(p -> p.sendNotification(to, email));
    }

    @Override
    public EmailChannelStatus channelStatus() {
        return storeConfig()
                .map(c -> EmailChannelStatus.of(true, "RESEND",
                        "Resend configurado em Dados da loja > Integrações (remetente " + c.fromEmail() + ")"))
                .orElseGet(fallback::channelStatus);
    }

    private void route(Consumer<EmailPort> send) {
        Optional<EmailSenderConfig> config = storeConfig();
        if (config.isEmpty()) {
            send.accept(fallback);
            return;
        }
        ResendEmailAdapter adapter = storeAdapterFactory.adapterFor(config.get());
        emailExecutor.execute(() -> {
            try {
                send.accept(adapter);
            } catch (Exception ex) {
                // Já registrado (log + email.failed.total) pelo adapter; aqui só não deixa vazar.
                log.debug("email.store-resend.failed error={}", ex.getMessage());
            }
        });
    }

    private Optional<EmailSenderConfig> storeConfig() {
        try {
            return integration.activeSenderConfig();
        } catch (Exception ex) {
            log.error("email.integration.read.failed — usando provedor do ambiente error={}", ex.getMessage());
            return Optional.empty();
        }
    }
}
