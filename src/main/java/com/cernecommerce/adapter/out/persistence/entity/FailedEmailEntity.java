package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "failed_emails")
public class FailedEmailEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String type;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String html;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resend_count", nullable = false)
    private int resendCount;

    @Column(name = "last_resend_at")
    private Instant lastResendAt;

    @Column(name = "last_resend_status", length = 10)
    private String lastResendStatus;

    @Column(name = "last_resend_error", columnDefinition = "TEXT")
    private String lastResendError;
}
