package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.domain.model.auth.UserSecurityInfo;

import java.util.List;

/** Ações de um admin sobre a segurança da conta de outra pessoa. Todas devolvem ou recebem o id do alvo. */
public interface UserSecurityUseCase {

    /** Mesmo e-mail do "esqueci minha senha". */
    String sendPasswordReset(Long id);

    /** Obriga a troca no próximo login e encerra as sessões. A senha não vai por e-mail. */
    String setTemporaryPassword(Long id, String temporaryPassword);

    UserSecurityInfo getSecurity(Long id);

    /** Ao ligar, avisa por e-mail. Idempotente: repetir não manda de novo. */
    String setTwoFactorRequired(Long id, boolean required);

    /** Para quem perdeu o celular. Não mexe em {@code twoFactorRequired}. */
    String resetTwoFactor(Long id);

    List<SessionInfo> listSessions(Long id);

    String revokeSessions(Long id);

    String revokeSession(Long id, Long sessionId);

    /** Troca na hora, sem código de confirmação, e avisa o endereço antigo. */
    String forceEmail(Long id, String email);

    /** @return username do alvo, para o avatar e a auditoria */
    String usernameOf(Long id);

    /**
     * A conta volta inativa, sem 2FA, avatar e vínculo, que a exclusão apagou.
     *
     * @param allowDevAccount quem chama tem acesso elevado: pode restaurar conta de dev
     */
    String restore(Long id, boolean allowDevAccount);
}
