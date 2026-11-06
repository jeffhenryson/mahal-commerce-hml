package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
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
    public void sendEmailChangeCode(String to, String username, String code) {
        log.info("DEV EMAIL >> to={} username={} emailChangeCode={}", to, username, code);
        lastCodeByUsername.put(username, code);
    }

    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink, long ttlMinutes) {
        log.info("DEV EMAIL >> to={} username={} passwordResetLink={} ttlMinutes={}", to, username, resetLink, ttlMinutes);
        lastCodeByUsername.put("reset:" + username, resetLink);
    }

    @Override
    public void sendUserInvite(String to, String username, String roles, String inviteLink, long ttlHours) {
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
    public void sendAccountChange(String to, String username, AccountChange change, String role) {
        log.info("DEV EMAIL >> to={} username={} change={} [account-change]", to, username, change);
    }

    @Override
    public void sendPasswordResetAlert(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:password-reset]", to, username, context);
    }

    @Override
    public void resendRendered(String to, String subject, String html, String type) {
        log.info("DEV EMAIL >> to={} subject={} [resend:{}]", to, subject, type);
    }

    @Override
    public void sendTwoFactorRequired(String to, String username) {
        log.info("DEV EMAIL >> to={} username={} [two-factor-required]", to, username);
    }

    @Override
    public void sendEmailChangedByAdmin(String to, String username, SecurityEventContext context) {
        log.info("DEV EMAIL >> to={} username={} context={} [security-alert:email-changed-by-admin]", to, username,
                context);
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
    public void sendCustomerWelcome(String to, String firstName) {
        log.info("DEV EMAIL >> to={} firstName={} [customer-welcome]", to, firstName);
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
    public void sendOrderStatusUpdate(String to, OrderEmailView order, OrderStageEmail stage) {
        log.info("DEV EMAIL >> to={} order={} stage={} [order-status-update]",
                to, order.orderReference(), stage.title());
    }

    @Override
    public void sendOrderCancellation(String to, OrderEmailView order, OrderClosureEmail email) {
        log.info("DEV EMAIL >> to={} order={} subject={} [order-cancellation]",
                to, order.orderReference(), email.subject());
    }

    @Override
    public void sendPurchaseReceipt(String to, OrderEmailView order, PurchaseReceiptEmail receipt) {
        log.info("DEV EMAIL >> to={} receipt={} total={} [purchase-receipt]", to, receipt.reference(), order.total());
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
