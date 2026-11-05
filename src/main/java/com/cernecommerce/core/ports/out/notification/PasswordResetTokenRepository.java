package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.auth.PasswordResetPurpose;
import com.cernecommerce.core.domain.model.auth.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository {
    PasswordResetToken save(String username, String rawToken, Instant expiresAt, PasswordResetPurpose purpose);

    Optional<PasswordResetToken> findByToken(String rawToken);

    /**
     * Marca atomicamente o token como usado (CAS: usedAt=null → usedAt=now).
     * Retorna true se foi reclamado por esta chamada; false se já estava usado.
     */
    boolean markAsUsed(String rawToken);

    void deleteByUsername(String username);

    void deleteExpiredBefore(Instant before);
}
