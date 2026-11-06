package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.auth.UserSecurityInfo;

import java.time.Instant;

public record UserSecurityResponseDTO(boolean totpEnabled, boolean twoFactorRequired, int backupCodesRemaining,
        boolean mustChangePassword, Instant lastLoginAt, String lastLoginIp) {

    public static UserSecurityResponseDTO of(UserSecurityInfo info) {
        return new UserSecurityResponseDTO(info.totpEnabled(), info.twoFactorRequired(), info.backupCodesRemaining(),
                info.mustChangePassword(), info.lastLoginAt(), info.lastLoginIp());
    }
}
