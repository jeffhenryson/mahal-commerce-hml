package com.cernecommerce.adapter.out.email;

import com.cernecommerce.core.domain.model.config.EmailSample;
import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
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

    private static final String IF_IT_WAS_YOU = "Se foi você, não precisa fazer nada.";
    private static final String NOT_YOU = "Não foi você?";
    private static final String CREATE_NEW_PASSWORD = "Crie uma nova senha agora. Isso desconecta todos os aparelhos.";
    private static final String TALK_TO_STORE = "Falar com a loja no WhatsApp";

    private record SecurityAlert(String subject, String preheader, String title, String message, String calloutLabel,
            String calloutText, String accent, String actionUrl, String actionLabel) {
    }

    private final ThreadLocal<Boolean> resending = new ThreadLocal<>();
    private final long ttlMinutes;
    private final String emailSubject;
    private final String verificationFrontendUrl;
    private final String emailChangeFrontendUrl;
    protected final ThymeleafEmailRenderer renderer;
    protected final MeterRegistry meterRegistry;
    private volatile Supplier<DevAlertPort> devAlerts = () -> null;

    protected TemplatedEmailAdapter(long ttlMinutes, String emailSubject, String verificationFrontendUrl,
            String emailChangeFrontendUrl, ThymeleafEmailRenderer renderer, MeterRegistry meterRegistry) {
        this.ttlMinutes = ttlMinutes;
        this.emailSubject = emailSubject;
        this.verificationFrontendUrl = verificationFrontendUrl;
        this.emailChangeFrontendUrl = emailChangeFrontendUrl;
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
    protected void reportFailure(String type, String to, String error, String subject, String html) {
        // O reenvio manual já mostra o erro a quem pediu, e não pode virar uma nova falha guardada.
        if (resending.get() != null) {
            return;
        }
        try {
            DevAlertPort port = devAlerts.get();
            if (port != null) {
                port.emailDeliveryFailed(type, to, error, subject, html);
            }
        } catch (Exception ignored) {
            // O log e o contador email.failed.total da subclasse já registram a falha.
        }
    }

    /** Síncrono: quem pede o reenvio espera o resultado. */
    @Override
    public void resendRendered(String to, String subject, String html, String type) {
        resending.set(Boolean.TRUE);
        try {
            deliver(to, subject, html, type);
        } finally {
            resending.remove();
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
                "ttlMinutes", ttlMinutes,
                "preheader", "Falta só um clique. O link vale por " + ttlMinutes + " minutos."));
        deliver(to, emailSubject, html, "email.verification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeCode(String to, String username, String code) {
        String html = renderer.render("email-change-request", vars(
                "username", username,
                "confirmUrl", emailChangeFrontendUrl + "?code=" + code,
                "ttlMinutes", ttlMinutes,
                "preheader", "A troca só vale depois da sua confirmação. O link vale por " + ttlMinutes + " minutos."));
        deliver(to, "Confirme seu novo e-mail", html, "email.email-change-request");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetLink(String to, String username, String resetLink, long resetTtlMinutes) {
        String html = renderer.render("password-reset", vars(
                "username", username,
                "resetLink", resetLink,
                "ttlMinutes", resetTtlMinutes,
                "preheader", "O link vale por " + resetTtlMinutes + " minutos. Se não foi você, pode ignorar."));
        deliver(to, "Redefina sua senha da " + renderer.storeName(), html, "email.password-reset");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendUserInvite(String to, String username, String roles, String inviteLink, long ttlHours) {
        String html = renderer.render("user-invite", vars(
                "username", username,
                "roles", roles,
                "inviteLink", inviteLink,
                "ttlHours", ttlHours,
                "preheader", "Crie sua senha para entrar. O link vale por " + ttlHours + " horas."));
        deliver(to, "Seu acesso ao painel da " + renderer.storeName(), html, "email.user-invite");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeNotification(String oldEmail, String username, String newEmail) {
        String html = renderer.render("email-change", vars(
                "username", username,
                "newEmail", newEmail,
                "supportUrl", renderer.whatsappUrl("Oi! Recebi um aviso de troca de e-mail da minha conta e não fui eu."),
                "preheader", "Agora a conta usa outro endereço. Se não foi você, fale com a loja."));
        deliver(oldEmail, "O e-mail da sua conta foi alterado", html, "email.email-change-notification");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangeConfirmed(String newEmail, String username) {
        String html = renderer.render("email-change", vars(
                "username", username,
                "newEmail", newEmail,
                "confirmed", true,
                "preheader", "Os avisos da sua conta agora chegam aqui."));
        deliver(newEmail, "Seu novo e-mail já está valendo", html, "email.email-change-confirmed");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendAccountChange(String to, String username, AccountChange change, String role) {
        String store = renderer.storeName();
        String subject = change == AccountChange.DISABLED
                ? "Sua conta na " + store + " foi desativada"
                : "Seu acesso ao painel da " + store + " mudou";
        String preheader = switch (change) {
            case ROLE_ASSIGNED -> "Você agora tem o papel " + role + ".";
            case ROLE_REMOVED -> "O papel " + role + " saiu da sua conta.";
            case DISABLED -> "Você não consegue mais entrar. Fale com a loja se precisar de ajuda.";
        };
        String html = renderer.render("account-change", vars(
                "username", username,
                "change", change.name(),
                "role", role,
                "preheader", preheader));
        deliver(to, subject, html, "email.account-change");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendWelcome(String to, String username) {
        String html = renderer.render("welcome", vars(
                "username", username,
                "appUrl", renderer.appUrl("/auth/login"),
                "preheader", "Entre no painel com o usuário " + username + "."));
        deliver(to, "Sua conta na " + renderer.storeName() + " está pronta", html, "email.welcome");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendCustomerWelcome(String to, String firstName) {
        String html = renderer.render("customer-welcome", vars(
                "firstName", firstName,
                "shopUrl", renderer.marketUrl("/"),
                "preheader", "Sua conta está pronta. Seus pedidos ficam todos num lugar só."));
        deliver(to, "Boas-vindas à " + renderer.storeName() + ", " + firstName + "!", html, "email.customer-welcome");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordChangedAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "Sua senha da " + renderer.storeName() + " foi alterada", IF_IT_WAS_YOU,
                "Sua senha foi alterada", "A senha da sua conta foi alterada.",
                NOT_YOU, CREATE_NEW_PASSWORD, EmailTheme.DEFAULT.warning(),
                recoveryUrl(context), "Recuperar minha conta"), "email.security-alert.password-changed");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendAccountLockedAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "Bloqueamos sua conta na " + renderer.storeName() + " por alguns minutos",
                "Foram várias tentativas de entrada com a senha errada.",
                "Sua conta está bloqueada por alguns minutos",
                "Bloqueamos a entrada na sua conta depois de várias tentativas com a senha errada.",
                "Foi você?", "Espere alguns minutos e tente de novo, ou crie uma nova senha. Se não foi você, crie uma nova senha por precaução.",
                EmailTheme.DEFAULT.warning(), recoveryUrl(context), "Criar nova senha"),
                "email.security-alert.account-locked");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTotpStatusAlert(String to, String username, boolean enabled, SecurityEventContext context) {
        String action = enabled ? "ativada" : "desativada";
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "A verificação em duas etapas foi " + action, IF_IT_WAS_YOU,
                "Verificação em duas etapas " + action,
                enabled ? "Agora sua conta pede um código extra ao entrar." : "Sua conta deixou de pedir o código extra ao entrar.",
                NOT_YOU, CREATE_NEW_PASSWORD, EmailTheme.DEFAULT.warning(),
                recoveryUrl(context), "Recuperar minha conta"),
                "email.security-alert.totp-" + (enabled ? "enabled" : "disabled"));
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTokenTheftAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "Desconectamos sua conta na " + renderer.storeName() + " por segurança",
                "Você precisa entrar de novo. Recomendamos criar uma nova senha.",
                "Desconectamos sua conta por segurança",
                "Notamos um uso estranho do acesso à sua conta e, por precaução, desconectamos todos os aparelhos. Para continuar, entre de novo.",
                "Recomendamos criar uma nova senha,", "principalmente se você não reconhece o aparelho acima.",
                EmailTheme.DEFAULT.danger(), recoveryUrl(context), "Criar nova senha"),
                "email.security-alert.token-theft");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPasswordResetAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "Sua senha da " + renderer.storeName() + " foi redefinida", IF_IT_WAS_YOU,
                "Sua senha foi redefinida", "A senha da sua conta foi trocada pelo link enviado para este e-mail.",
                NOT_YOU, "Alguém pode ter acesso ao seu e-mail. Troque a senha do e-mail primeiro e depois fale com a loja.",
                EmailTheme.DEFAULT.warning(), supportUrl(), TALK_TO_STORE), "email.security-alert.password-reset");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendGoogleLinkedAlert(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "Login com Google ativado na sua conta", IF_IT_WAS_YOU,
                "Login com Google ativado", "Agora você também pode entrar na sua conta com o Google deste e-mail.",
                NOT_YOU, "Fale com a loja agora.", EmailTheme.DEFAULT.warning(), supportUrl(), TALK_TO_STORE),
                "email.security-alert.google-linked");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendTwoFactorRequired(String to, String username) {
        sendSecurityAlert(to, username, null, new SecurityAlert(
                "Sua conta vai pedir o código de verificação", "Falta só um passo para proteger sua conta.",
                "Verificação em duas etapas exigida",
                "No próximo login, você vai ativar a verificação em duas etapas. Antes, instale no celular o "
                        + "Google Authenticator ou o Microsoft Authenticator, pela App Store ou pela Google Play.",
                "Precisa de ajuda?", "Fale com quem cuida dos acessos da loja.", EmailTheme.DEFAULT.warning(),
                supportUrl(), TALK_TO_STORE), "email.security-alert.two-factor-required");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendEmailChangedByAdmin(String to, String username, SecurityEventContext context) {
        sendSecurityAlert(to, username, context, new SecurityAlert(
                "O e-mail da sua conta foi trocado", "Este endereço não recebe mais os avisos da conta.",
                "E-mail da conta trocado", "Um administrador da loja trocou o e-mail de acesso da sua conta.",
                NOT_YOU, "Fale com a loja agora.", EmailTheme.DEFAULT.warning(), supportUrl(), TALK_TO_STORE),
                "email.security-alert.email-changed-by-admin");
    }

    /** A recuperação encerra todas as sessões, então também serve de "desconectar tudo". */
    private String recoveryUrl(SecurityEventContext context) {
        return context != null && context.customer()
                ? renderer.marketUrl("/conta")
                : renderer.appUrl("/auth/forgot-password");
    }

    private String supportUrl() {
        return renderer.whatsappUrl("Oi! Recebi um aviso de segurança da minha conta e não fui eu.");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendOrderConfirmation(String to, OrderEmailView order, String checkoutUrl) {
        String reference = order.orderReference();
        String html = renderer.render("order-confirmation", vars(
                "order", order,
                "checkoutUrl", checkoutUrl,
                "trackingUrl", orderUrl(order),
                "preheader", confirmationPreheader(order, checkoutUrl)));
        String subject = checkoutUrl != null ? "Falta só pagar o pedido " + reference : "Pedido " + reference + " na área!";
        deliver(to, subject, html, "email.order-confirmation");
    }

    private static String confirmationPreheader(OrderEmailView order, String checkoutUrl) {
        if (checkoutUrl != null) {
            return "Conclua o pagamento e a gente já começa a separar.";
        }
        return order.delivery() ? "A loja confere tudo e já te manda o total com o frete."
                : "A loja confere tudo e te avisa assim que aceitar.";
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendOrderStatusUpdate(String to, OrderEmailView order, OrderStageEmail stage) {
        boolean ride = stage.rideUrl() != null;
        String html = renderer.render("order-status-update", vars(
                "order", order,
                "stage", stage,
                "preheader", stage.preheader(),
                "actionUrl", ride ? stage.rideUrl() : (stage.tracking() ? orderUrl(order) : null),
                "actionLabel", ride ? "Acompanhar entrega" : "Acompanhar pedido"));
        deliver(to, stage.subject(), html, "email.order-status");
    }

    /** O pedido no app do cliente; {@code null} se não há pedido real ou {@code email.market-base-url}. */
    private String orderUrl(OrderEmailView order) {
        return order.orderId() == null ? null : renderer.marketUrl("/conta/pedidos/" + order.orderId());
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendOrderCancellation(String to, OrderEmailView order, OrderClosureEmail email) {
        String actionUrl = null;
        String actionLabel = null;
        if (email.whatsapp()) {
            actionUrl = renderer.whatsappUrl("Oi! Quero combinar a devolução do pedido " + order.orderReference());
            actionLabel = "Falar no WhatsApp";
        } else if (email.newOrder()) {
            actionUrl = renderer.marketUrl("/");
            actionLabel = "Fazer novo pedido";
        }
        String html = renderer.render("order-cancellation", vars(
                "order", order,
                "email", email,
                "preheader", email.preheader(),
                "actionUrl", actionUrl,
                "actionLabel", actionLabel));
        deliver(to, email.subject(), html, "email.order-cancellation");
    }

    @Async("emailTaskExecutor")
    @Override
    public void sendPurchaseReceipt(String to, OrderEmailView order, PurchaseReceiptEmail receipt) {
        String html = renderer.render("purchase-receipt", vars(
                "order", order,
                "receipt", receipt,
                "preheader", receipt.preheader()));
        deliver(to, "Comprovante da sua compra " + receipt.reference(), html, "email.purchase-receipt");
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
                "preheader", email.preheader() != null ? email.preheader() : email.intro(),
                "actionUrl", email.whatsappMessage() != null
                        ? renderer.whatsappUrl(email.whatsappMessage())
                        : renderer.appUrl(email.actionPath())));
    }

    /**
     * Envia um exemplo de {@code sample} com dados fictícios, na thread de quem chama (chamada
     * interna: não passa pelo proxy do {@code @Async}).
     */
    void sendSample(String to, EmailSample sample) {
        EmailSamples.send(this, to, sample);
    }

    private void sendSecurityAlert(String to, String username, SecurityEventContext context, SecurityAlert alert,
            String type) {
        String html = renderer.render("security-alert", vars(
                "username", username,
                "title", alert.title(),
                "message", alert.message(),
                "calloutLabel", alert.calloutLabel(),
                "footerMessage", alert.calloutText(),
                "accent", alert.accent(),
                "actionUrl", alert.actionUrl(),
                "actionLabel", alert.actionLabel(),
                "preheader", alert.preheader(),
                "occurredAt", context == null ? null : EmailFormat.dateTime(context.occurredAt()),
                "ip", context == null ? null : context.ip(),
                "device", context == null ? null : DeviceLabel.of(context.userAgent())));
        deliver(to, alert.subject(), html, type);
    }

    private static String accent(NotificationEmail.Tone tone) {
        return switch (tone) {
            case SUCCESS -> EmailTheme.DEFAULT.success();
            case WARNING -> EmailTheme.DEFAULT.warning();
            case DANGER -> EmailTheme.DEFAULT.danger();
            case INFO -> EmailTheme.DEFAULT.brand();
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
