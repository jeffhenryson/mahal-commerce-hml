package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.notification.FailedEmailNotFoundException;
import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.domain.model.notification.FailedEmail;
import com.cernecommerce.core.ports.in.FailedEmailUseCase;
import com.cernecommerce.core.ports.out.notification.EmailPort;
import com.cernecommerce.core.ports.out.notification.FailedEmailRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class FailedEmailService implements FailedEmailUseCase {

    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(30);

    private final FailedEmailRepository repository;
    private final EmailPort emailPort;
    private final Clock clock;

    public FailedEmailService(FailedEmailRepository repository, EmailPort emailPort, Clock clock) {
        this.repository = repository;
        this.emailPort = emailPort;
        this.clock = clock;
    }

    @Override
    public FailedEmail get(Long id) {
        return repository.findById(id).orElseThrow(() -> new FailedEmailNotFoundException(id));
    }

    @Override
    public FailedEmail resend(Long id) {
        FailedEmail email = get(id);
        Instant now = clock.instant();
        if (email.lastResendAt() != null) {
            Duration elapsed = Duration.between(email.lastResendAt(), now);
            if (elapsed.compareTo(RESEND_INTERVAL) < 0) {
                throw new RateLimitExceededException("failed-email-resend",
                        RESEND_INTERVAL.minus(elapsed).toSeconds() + 1);
            }
        }
        try {
            emailPort.resendRendered(email.recipient(), email.subject(), email.html(), email.type());
            return repository.save(email.afterResend(now, FailedEmail.RESEND_SENT, null));
        } catch (RuntimeException ex) {
            return repository.save(email.afterResend(now, FailedEmail.RESEND_FAILED, ex.getMessage()));
        }
    }
}
