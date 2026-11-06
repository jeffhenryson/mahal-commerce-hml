package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.notification.FailedEmail;

public interface FailedEmailUseCase {

    FailedEmail get(Long id);

    /**
     * Reenvia com o mesmo assunto e HTML. Se o provedor recusar de novo, devolve o registro com
     * {@code lastResendStatus = FAILED}: não é um erro da chamada.
     *
     * @throws com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException reenviado há pouco
     */
    FailedEmail resend(Long id);
}
