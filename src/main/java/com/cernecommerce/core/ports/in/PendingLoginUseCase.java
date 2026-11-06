package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.auth.LoginResponse;
import com.cernecommerce.core.domain.model.auth.TokenPair;

import java.util.List;

/** Conclui as pendências do login. O token de pendência é a autorização: as rotas são públicas. */
public interface PendingLoginUseCase {

    /** @return tokens, ou o token de cadastro de 2FA se essa exigência também estiver pendente */
    LoginResponse changeRequiredPassword(String passwordChangeToken, String newPassword);

    TotpUseCase.TotpSetupResult startEnrollment(String enrollmentToken);

    EnrollmentResult confirmEnrollment(String enrollmentToken, String totpCode);

    record EnrollmentResult(List<String> backupCodes, TokenPair tokens) {
    }
}
