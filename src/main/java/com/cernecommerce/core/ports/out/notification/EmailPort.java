package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.EmailChannelStatus;
import com.cernecommerce.core.domain.model.notification.NotificationEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.SecurityEventContext;

public interface EmailPort {
    void sendVerificationCode(String to, String username, String code);

    void sendPasswordResetLink(String to, String username, String resetLink, long ttlMinutes);

    /** Convite de usuário criado por dev/admin: link para definir a senha (token de redefinição). */
    void sendUserInvite(String to, String username, String inviteLink, long ttlHours);

    void sendEmailChangeNotification(String oldEmail, String username, String newEmail);

    /** Confirmação, no endereço NOVO, de que a troca de e-mail foi concluída. */
    void sendEmailChangeConfirmed(String newEmail, String username);

    /** Papel atribuído/removido ou conta desativada por um administrador. */
    void sendAccountChange(String to, String username, String title, String message);

    /** Boas-vindas depois de verificar o e-mail ou de ter a conta criada por um admin. */
    void sendWelcome(String to, String username);

    void sendPasswordChangedAlert(String to, String username, SecurityEventContext context);

    void sendAccountLockedAlert(String to, String username, SecurityEventContext context);

    void sendTotpStatusAlert(String to, String username, boolean enabled, SecurityEventContext context);

    void sendTokenTheftAlert(String to, String username, SecurityEventContext context);

    /** Senha redefinida pelo link de recuperação (sem estar logado). */
    void sendPasswordResetAlert(String to, String username, SecurityEventContext context);

    /** Primeiro login com Google — conta criada ou vinculada ao Google. */
    void sendGoogleLinkedAlert(String to, String username, SecurityEventContext context);

    /** Confirmação de pedido criado, disparada no checkout (marketplace). */
    void sendOrderConfirmation(String to, OrderEmailView order, String checkoutUrl);

    /** Aviso de mudança de status do pedido (pagamento confirmado, separado, enviado, entregue). */
    void sendOrderStatusUpdate(String to, OrderEmailView order, String newStatusLabel);

    /**
     * Aviso de pedido cancelado ou reembolsado — {@code refunded} muda o texto entre os dois casos;
     * {@code reason} nulo/vazio omite o bloco de motivo.
     */
    void sendOrderCancellation(String to, OrderEmailView order, String reason, boolean refunded);

    /** Comprovante de compra presencial (balcão/mesa), enviado só quando o operador pede. */
    void sendPurchaseReceipt(String to, OrderEmailView order);

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
