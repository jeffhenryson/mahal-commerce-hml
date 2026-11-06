package com.cernecommerce.core.domain.model.auth;

import java.time.Instant;

public record UserSecurityInfo(boolean totpEnabled, boolean twoFactorRequired, int backupCodesRemaining,
        boolean mustChangePassword, Instant lastLoginAt, String lastLoginIp) {
}
