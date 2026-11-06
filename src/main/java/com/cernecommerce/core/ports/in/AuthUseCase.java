package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.auth.DevElevationResult;
import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.SessionInfo;
import com.cernecommerce.core.domain.model.auth.TokenPair;

import java.util.List;

public interface AuthUseCase {
    LoginResponse login(String username, String password);

    /** Conclui o login após validação do código TOTP ou backup code. */
    LoginResponse completeTwoFactorLogin(String challengeToken, String totpCode);

    /** Grava o último acesso, depois de emitir os tokens. Não vale para o refresh. */
    void recordLogin(String username, String ip);

    /**
     * Conclui a elevação DEV: valida o segundo TOTP consecutivo e emite um access token
     * com a authority DEV_ELEVATED. Sem refresh token — sessão DEV expira com o access token.
     * Retorna username + token para que o controller possa publicar o evento de auditoria.
     */
    DevElevationResult completeDevElevation(String rawDevToken, String secondTotpCode);

    TokenPair refresh(String oldRefreshToken);

    void logout(String refreshToken);

    void logoutAll(String username);

    List<SessionInfo> listActiveSessions(String username);

    void revokeSession(Long sessionId, String username);
}
