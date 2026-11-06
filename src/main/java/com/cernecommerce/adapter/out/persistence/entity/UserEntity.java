package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.auth.AuthProvider;
import com.cernecommerce.core.domain.model.auth.UserType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SQLRestriction("deleted_at IS NULL")
@Table(name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_username", columnNames = {"user_type", "username"}),
                @UniqueConstraint(name = "uk_user_email", columnNames = "email")
        })
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Unicidade composta com user_type (V76) — a aplicação nunca deixa a colisão acontecer de
    // fato (UserService checa username+email cruzando os dois tipos antes de criar conta de
    // cliente); a constraint é rede de segurança, não o mecanismo principal.
    @Column(nullable = false, length = 80)
    private String username;

    @Column(length = 255)
    private String password;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 254)
    private String email;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(name = "pending_email", length = 254)
    private String pendingEmail;

    @Column(name = "avatar_filename", length = 64)
    private String avatarFilename;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "google_id", length = 255)
    private String googleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", length = 20, nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20, nullable = false)
    private UserType userType = UserType.OPERATOR;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "full_name", length = 160)
    private String fullName;

    @Column(length = 32)
    private String phone;

    @Column(name = "two_factor_required", nullable = false)
    private boolean twoFactorRequired = false;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_login_ip", length = 64)
    private String lastLoginIp;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_roles_user")),
            inverseJoinColumns = @JoinColumn(name = "role_id", foreignKey = @ForeignKey(name = "fk_user_roles_role")))
    @ToString.Exclude
    private Set<RoleEntity> roles = new HashSet<>();
}