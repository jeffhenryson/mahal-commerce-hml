package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.auth.OAuthLoginResult;

public interface OAuthLoginUseCase {
    OAuthLoginResult loginWithGoogle(String idToken);

    /** Grava o último acesso, depois de emitir os tokens. */
    void recordLogin(String username, String ip);
}
