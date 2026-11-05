package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.auth.PasswordResetPurpose;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "password_reset_tokens",
        indexes = @Index(name = "idx_prt_token_hash", columnList = "token_hash"),
        uniqueConstraints = @UniqueConstraint(name = "uk_prt_token_hash", columnNames = "token_hash"))
public class PasswordResetTokenEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, updatable = false)
    private Instant requestedAt;

    @Column
    private Instant usedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PasswordResetPurpose purpose = PasswordResetPurpose.RESET;

    @PrePersist
    void prePersist() {
        if (requestedAt == null) requestedAt = Instant.now();
    }
}
