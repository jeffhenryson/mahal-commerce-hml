package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.AccountChange;
import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.notification.PurchaseReceiptEmail;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);

    /** Pedido de confirmação, no endereço NOVO, para concluir a troca de e-mail da conta. */
    void sendEmailChangeCode(String to, String username, String code);

    /** @param username nome usado na saudação: o primeiro nome, para o cliente do app. */
    void sendPasswordResetLink(String to, String username, String resetLink, long ttlMinutes);

    /**
     * Convite de usuário criado por dev/admin: link para definir a senha (token de redefinição).
     *
     * @param roles papéis em texto legível, ou {@code null} sem papel
     */
    void sendUserInvite(String to, String username, String roles, String inviteLink, long ttlHours);

    void sendEmailChangeNotification(String oldEmail, String username, String newEmail);

    /** Confirmação, no endereço NOVO, de que a troca de e-mail foi concluída. */
    void sendEmailChangeConfirmed(String newEmail, String username);

    /**
     * Papel atribuído/removido ou conta desativada por um administrador.
     *
     * @param username nome usado na saudação: o primeiro nome, para o cliente do app
     * @param role nome legível do papel, ou {@code null} em {@link AccountChange#DISABLED}
     */
    void sendAccountChange(String to, String username, AccountChange change, String role);

    /** Boas-vindas de quem teve a conta do painel criada por um admin, já com senha. */
    void sendWelcome(String to, String username);

    /** Boas-vindas do cliente que acabou de se cadastrar no app. */
    void sendCustomerWelcome(String to, String firstName);

    void sendPasswordChangedAlert(String to, String username, SecurityEventContext context);

    void sendAccountLockedAlert(String to, String username, SecurityEventContext context);

    void sendTotpStatusAlert(String to, String username, boolean enabled, SecurityEventContext context);

    void sendTokenTheftAlert(String to, String username, SecurityEventContext context);

    /** Senha redefinida pelo link de recuperação (sem estar logado). */
    void sendPasswordResetAlert(String to, String username, SecurityEventContext context);

    /** Primeiro login com Google — conta criada ou vinculada ao Google. */
    void sendGoogleLinkedAlert(String to, String username, SecurityEventContext context);

    /**
     * Reenvia uma mensagem já renderizada, de forma síncrona.
     *
     * @throws com.cernecommerce.core.domain.exception.email.EmailDeliveryException se o provedor recusar
     */
    void resendRendered(String to, String subject, String html, String type);

    /** Um admin exigiu 2FA: o próximo login vai pedir a ativação. */
    void sendTwoFactorRequired(String to, String username);

    /** Vai para o endereço antigo, depois que um admin trocou o e-mail da conta. */
    void sendEmailChangedByAdmin(String to, String username, SecurityEventContext context);

    /** Confirmação de pedido criado, disparada no checkout (marketplace). */
    void sendOrderConfirmation(String to, OrderEmailView order, String checkoutUrl);

    /** Aviso de nova etapa do pedido (aceito, pago, pronto, saiu para entrega, entregue). */
    void sendOrderStatusUpdate(String to, OrderEmailView order, OrderStageEmail stage);

    /**
     * Aviso de pedido cancelado ou reembolsado. {@code order} é o que a lista mostra: no reembolso
     * parcial, só os itens devolvidos.
     */
    void sendOrderCancellation(String to, OrderEmailView order, OrderClosureEmail email);

    /** Comprovante de compra presencial (balcão/mesa), enviado só quando o operador pede. */
    void sendPurchaseReceipt(String to, OrderEmailView order, PurchaseReceiptEmail receipt);

    /**
     * Aviso ao cliente no formato de {@link NotificationEmail} (fiado: compra marcada, vencimento,
     * quitação). Ao contrário de {@link #sendNotification}, sem o prefixo da loja no assunto.
     */
    void sendCustomerNotice(String to, NotificationEmail email);

    /** E-mail operacional para gestores e devs (caixa, operação, estoque, alertas técnicos). */
    void sendNotification(String to, NotificationEmail email);

    /** Status de conexão do adapter de e-mail atualmente ativo (ver crm/integracao-canal-envio, F008). */
    EmailChannelStatus channelStatus();
}
