package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter de email para desenvolvimento: exibe o código no log em vez de enviá-lo.
 * Em hml/prod, substituído pelo ResendEmailAdapter.
 *
 * Também retém o último código enviado por username para que testes de integração
 * possam recuperar o código em texto puro sem precisar inverter o hash.
 */
public class LoggingEmailAdapter implements EmailPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailAdapter.class);

    private final Map<String, String> lastCodeByUsername = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationCode(String to, String username, String code) {
        log.info("DEV EMAIL >> to={} username={} verificationCode={}", to, username, code);
        lastCodeByUsername.put(username, code);
    }

    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink, long ttlMinutes) {
        log.info("DEV EMAIL >> to={} username={} passwordResetLink={} ttlMinutes={}", to, username, resetLink, ttlMinutes);
        lastCodeByUsername.put("reset:" + username, resetLink);
    }

    @Override
    public void sendUserInvite(String to, String username, String inviteLink, long ttlHours) {
        log.info("DEV EMAIL >> to={} username={} inviteLink={} ttlHours={}", to, username, inviteLink, ttlHours);
        lastCodeByUsername.put("invite:" + username, inviteLink);
    }

    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        log.info("DEV EMAIL >> oldEmail={} username={} newEmail={} [email-change-notification]",
                oldEmail, username, newEmail);
    }

    @Override
    public void sendEmailChangeConfirmed(String newEmail, String username) {
        log.info("DEV EMAIL >> to={} username={} [email-change-confirmed]", newEmail, username);
    }

    @Override
    public void sendAccountChange(String to, String username, String title, String message) {
        log.info("DEV EMAIL >> to={} username={} title={} [account-change]", to, username, title);
    }

    @Override
    public void sendPasswordResetAlert(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:password-reset]", to, username, context);
    }

    @Override
    public void sendGoogleLinkedAlert(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:google-linked]", to, username, context);
    }

    @Override
    public void sendCustomerNotice(String to, NotificationEmail email) {
        log.info("DEV EMAIL >> to={} category={} subject={} [customer-notice]", to, email.category(), email.subject());
    }

    @Override
    public void sendWelcome(String to, String username) {
        log.info("DEV EMAIL >> to={} username={} [welcome]", to, username);
    }

    @Override
    public void sendPasswordChangedAlert(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:password-changed]", to, username, context);
    }

    @Override
    public void sendAccountLockedAlert(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:account-locked]", to, username, context);
    }

    @Override
    public void sendTotpStatusAlert(String to, String username, boolean enabled, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} enabled={} context={} [security-alert:totp-status]",
                to, username, enabled, context);
    }

    @Override
    public void sendTokenTheftAlert(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:token-theft]", to, username, context);
    }

    @Override
    public void sendOrderConfirmation(String to, OrderEmailView order, String checkoutUrl) {
        log.info("DEV EMAIL >> to={} order={} total={} itemCount={} checkoutUrl={} [order-confirmation]",
                to, order.orderReference(), order.total(), order.itemCount(), checkoutUrl);
    }

    @Override
    public void sendOrderStatusUpdate(String to, OrderEmailView order, String newStatusLabel) {
        log.info("DEV EMAIL >> to={} order={} newStatusLabel={} [order-status-update]",
                to, order.orderReference(), newStatusLabel);
    }

    @Override
    public void sendOrderCancellation(String to, OrderEmailView order, String reason, boolean refunded) {
        log.info("DEV EMAIL >> to={} order={} reason={} refunded={} [order-cancellation]",
                to, order.orderReference(), reason, refunded);
    }

    @Override
    public void sendPurchaseReceipt(String to, OrderEmailView order) {
        log.info("DEV EMAIL >> to={} order={} total={} [purchase-receipt]", to, order.orderReference(), order.total());
    }

    @Override
    public void sendNotification(String to, NotificationEmail email) {
        log.info("DEV EMAIL >> to={} category={} subject={} [notification]", to, email.category(), email.subject());
    }

    @Override
    public EmailChannelStatus channelStatus() {
        return EmailChannelStatus.of(false, "LOG",
                "Ambiente sem provedor real configurado — e-mails apenas registrados em log");
    }

    /** Returns the last plain-text verification code or reset link sent to the given username. Test use only. */
    public String getLastCodeForUsername(String username) {
        return lastCodeByUsername.get(username);
    }

    /** Returns the last password-reset link sent for the given username. Test use only. */
    public String getLastResetLinkForUsername(String username) {
        return lastCodeByUsername.get("reset:" + username);
    }
}
