package com.cernecommerce.core.domain.model.auth;

import java.time.Instant;

/** Autoriza só a pendência de {@code purpose}. Guardado como hash, como o desafio de 2FA. */
public record PendingLoginToken(
        Long id,
        String username,
        String tokenHash,
        PendingLoginPurpose purpose,
        Instant expiresAt,
        Instant createdAt,
        Instant usedAt) {

    public static final int TTL_SECONDS = 600;

    public boolean isUsable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }
}
