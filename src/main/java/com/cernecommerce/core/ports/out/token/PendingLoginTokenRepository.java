package com.cernecommerce.core.ports.out.token;

import com.cernecommerce.core.domain.model.auth.PendingLoginPurpose;
import com.cernecommerce.core.domain.model.auth.PendingLoginToken;

import java.time.Instant;
import java.util.Optional;

public interface PendingLoginTokenRepository {

    PendingLoginToken save(String username, String rawToken, PendingLoginPurpose purpose, Instant expiresAt);

    Optional<PendingLoginToken> findByToken(String rawToken);

    /** @return {@code false} se outro pedido já usou o token */
    boolean markAsUsed(String rawToken);

    void deleteByUsername(String username);

    void deleteExpiredBefore(Instant before);
}
