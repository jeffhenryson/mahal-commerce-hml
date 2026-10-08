package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Async;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Monta os e-mails transacionais (template Thymeleaf + assunto) e deixa só o transporte para a
 * subclasse ({@link #deliver}). Resend e Mailpit eram cópias um do outro; o que muda entre eles é
 * o corpo do POST.
 *
 * <p>Variáveis do template vão num {@link HashMap} e não em {@code Map.of}: este último lança NPE
 * com valor nulo, e um {@code checkoutUrl} ausente (que o template já trata) derrubava o envio.</p>
 *
 * <p>{@code @Async} só vale quando a instância é um bean do Spring; instâncias criadas à mão (as do
 * Resend configurado na loja) enviam de forma síncrona e quem chama decide a thread.</p>
 */
abstract class TemplatedEmailAdapter implements EmailPort {

    private final long ttlMinutes;
    private final String emailSubject;
    private final String verificationFrontendUrl;
    protected final ThymeleafEmailRenderer renderer;
    protected final MeterRegistry meterRegistry;
    private volatile Supplier<DevAlertPort> devAlerts = () -> null;

    protected TemplatedEmailAdapter(long ttlMinutes, String emailSubject, String verificationFrontendUrl,
            ThymeleafEmailRenderer renderer, MeterRegistry meterRegistry) {
        this.ttlMinutes = ttlMinutes;
        this.emailSubject = emailSubject;
        this.verificationFrontendUrl = verificationFrontendUrl;
        this.renderer = renderer;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Para onde avisar as falhas de entrega (in-app dos devs). Supplier porque o {@link DevAlertPort}
     * depende do próprio {@code EmailPort}: resolvido só na hora da primeira falha.
     */
    void reportFailuresTo(Supplier<DevAlertPort> devAlerts) {
        this.devAlerts = devAlerts;
    }

    /** Chamado pela subclasse no {@code catch} do envio, antes de relançar. Nunca lança. */
    protected void reportFailure(String type, String to, String error) {
        try {
            DevAlertPort port = devAlerts.get();
            if (port != null) {
                port.emailDeliveryFailed(type, to, error);
            }
        } catch (Exception ignored) {
            // O log e o contador email.failed.total da subclasse já registram a falha.
        }
    }

    /** Entrega a mensagem já renderizada; lança {@code EmailDeliveryException} se o provedor recusar. */
    protected abstract void deliver(String to, String subject, String html, String type);

    @Async("emailTaskExecutor")
    @Override
    public void sendVerificationCode(String to, String username, String code) {
        String verifyUrl = verificationFrontendUrl + "?code=" + code;
        String html = renderer.render("verification-code", vars(
                "username", username,
                "code", code,
                "verifyUrl", verifyUrl,
                "ttlMinutes", ttlMinutes));
        deliver(to, emailSubject, html, "email.verification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink, long resetTtlMinutes) {
        String html = renderer.render("password-reset", vars(
                "username", username,
                "resetLink", resetLink,
                "ttlMinutes", resetTtlMinutes));
        deliver(to, "Recuperação de senha", html, "email.password-reset");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendUserInvite(String to, String username, String inviteLink, long ttlHours) {
        String html = renderer.render("user-invite", vars(
                "username", username,
                "inviteLink", inviteLink,
                "ttlHours", ttlHours));
        deliver(to, "Você foi convidado para o painel da loja", html, "email.user-invite");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        String html = renderer.render("email-change", vars(
                "username", username,
                "newEmail", newEmail));
        deliver(oldEmail, "Seu email foi alterado", html, "email.email-change-notification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeConfirmed(String newEmail, String username) {
        String html = renderer.render("email-change", vars(
                "username", username,
                "newEmail", newEmail,
                "confirmed", true));
        deliver(newEmail, "Este é o novo email da sua conta", html, "email.email-change-confirmed");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendAccountChange(String to, String username, String title, String message) {
        String html = renderer.render("role-changed", vars(
                "username", username,
                "title", title,
                "message", message));
        deliver(to, title, html, "email.account-change");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendWelcome(String to, String username) {
        String html = renderer.render("welcome", vars(
                "username", username,
                "appUrl", renderer.appUrl("/")));
        deliver(to, "Boas-vindas — " + renderer.storeName(), html, "email.welcome");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordChangedAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, "Sua senha foi alterada",
                "A senha da sua conta foi alterada.",
                "Se não foi você, entre em contato com o suporte imediatamente e revogue suas sessões.",
                "Alerta de segurança: senha alterada", "email.security-alert.password-changed");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendAccountLockedAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, "Conta temporariamente bloqueada",
                "Sua conta foi bloqueada temporariamente devido a múltiplas tentativas de login malsucedidas.",
                "Aguarde alguns minutos e tente novamente. Se não foi você, sua senha pode estar comprometida.",
                "Alerta de segurança: conta bloqueada", "email.security-alert.account-locked");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTotpStatusAlert(String to, String username, boolean enabled, SecurityEventContext context) {
        String action = enabled ? "ativada" : "desativada";
        sendSecurityAlert(to, username, context, "Autenticação em dois fatores " + action,
                "A autenticação em dois fatores foi " + action + " na sua conta.",
                "Se não foi você, acesse sua conta e revogue todas as sessões ativas imediatamente.",
                "Alerta de segurança: 2FA " + action, "email.security-alert.totp-" + (enabled ? "enabled" : "disabled"));
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTokenTheftAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, "Acesso suspeito detectado",
                "Detectamos o reuso de uma credencial de sessão já utilizada — todas as sessões da sua conta foram encerradas automaticamente.",
                "Se não foi você, sua conta pode estar comprometida. Troque sua senha imediatamente.",
                "Alerta de segurança: acesso suspeito", "email.security-alert.token-theft");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, "Sua senha foi redefinida",
                "A senha da sua conta foi redefinida pelo link de recuperação enviado para este e-mail.",
                "Se não foi você, alguém tem acesso ao seu e-mail. Troque a senha do e-mail e da conta imediatamente.",
                "Alerta de segurança: senha redefinida", "email.security-alert.password-reset");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendGoogleLinkedAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, "Login com Google ativado",
                "Sua conta passou a aceitar login com a conta Google deste e-mail.",
                "Se não foi você, entre em contato com o suporte imediatamente.",
                "Alerta de segurança: login com Google", "email.security-alert.google-linked");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendOrderConfirmation(String to, OrderEmailView order, String checkoutUrl) {
        String html = renderer.render("order-confirmation", vars(
                "order", order,
                "checkoutUrl", checkoutUrl));
        deliver(to, "Recebemos seu pedido " + order.orderReference(), html, "email.order-confirmation");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendOrderStatusUpdate(String to, OrderEmailView order, String newStatusLabel) {
        String html = renderer.render("order-status-update", vars(
                "order", order,
                "newStatusLabel", newStatusLabel));
        deliver(to, newStatusLabel + " — pedido " + order.orderReference(), html, "email.order-status");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendOrderCancellation(String to, OrderEmailView order, String reason, boolean refunded) {
        String html = renderer.render("order-cancellation", vars(
                "order", order,
                "reason", reason == null || reason.isBlank() ? null : reason,
                "refunded", refunded));
        String subject = refunded ? "Pedido " + order.orderReference() + " reembolsado"
                : "Pedido " + order.orderReference() + " cancelado";
        deliver(to, subject, html, "email.order-cancellation");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPurchaseReceipt(String to, OrderEmailView order) {
        String html = renderer.render("purchase-receipt", vars("order", order));
        deliver(to, "Comprovante da sua compra " + order.orderReference(), html, "email.purchase-receipt");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendNotification(String to, NotificationEmail email) {
        // Prefixo só no operacional: facilita filtrar a caixa do gestor/dev. O do comprador já vem
        // com o nome da loja no remetente.
        deliver(to, "[" + renderer.storeName() + "] " + email.subject(), renderNotification(email),
                "email." + email.category());
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendCustomerNotice(String to, NotificationEmail email) {
        deliver(to, email.subject(), renderNotification(email), "email." + email.category());
    }

    private String renderNotification(NotificationEmail email) {
        return renderer.render("notification", vars(
                "email", email,
                "accent", accent(email.tone()),
                "actionUrl", renderer.appUrl(email.actionPath())));
    }

    /**
     * Envia um exemplo de {@code sample} com dados fictícios, na thread de quem chama (chamada
     * interna: não passa pelo proxy do {@code @Async}).
     */
    void sendSample(String to, EmailSample sample) {
        EmailSamples.send(this, to, sample);
    }

    private void sendSecurityAlert(String to, String username, SecurityEventContext context, String title,
            String message, String footerMessage, String subject, String type) {
        String html = renderer.render("security-alert", vars(
                "username", username,
                "title", title,
                "message", message,
                "footerMessage", footerMessage,
                "occurredAt", context == null ? null : EmailFormat.dateTime(context.occurredAt()),
                "ip", context == null ? null : context.ip(),
                "device", context == null ? null : device(context.userAgent())));
        deliver(to, subject, html, type);
    }

    /** User agent cru é ilegível; corta no tamanho de uma linha. */
    private static String device(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > 120 ? userAgent.substring(0, 117) + "..." : userAgent;
    }

    private static String accent(NotificationEmail.Tone tone) {
        return switch (tone) {
            case SUCCESS -> "#15803d";
            case WARNING -> "#b45309";
            case DANGER -> "#b91c1c";
            case INFO -> "#1d4ed8";
        };
    }

    private static Map<String, Object> vars(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
