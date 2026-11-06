package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.notification.FailedEmail;

import java.time.Instant;
import java.util.Optional;

public interface FailedEmailRepository {

    FailedEmail save(FailedEmail email);

    Optional<FailedEmail> findById(Long id);

    void deleteCreatedBefore(Instant before);
}
