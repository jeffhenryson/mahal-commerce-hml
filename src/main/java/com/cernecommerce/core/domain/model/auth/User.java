package com.cernecommerce.core.domain.model.auth;

import com.cernecommerce.core.domain.exception.user.CustomerAccountLinkImmutableException;
import com.cernecommerce.core.domain.model.rbac.Role;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class User {

    private Long id;
    private String username;
    private String password;
    private boolean enabled = true;
    private String email;
    private boolean emailVerified = false;
    private String pendingEmail;
    private String avatarFilename;
    private Instant createdAt;
    private Set<Role> roles = new HashSet<>();
    private String googleId;
    private AuthProvider authProvider = AuthProvider.LOCAL;
    private UserType userType = UserType.OPERATOR;
    private Long customerId;

    User() {
    }

    public static User of(String username, String hashedPassword, Set<Role> roles) {
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(hashedPassword, "password is required");
        User u = new User();
        u.username = username;
        u.password = hashedPassword;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        return u;
    }

    public static User ofPendingVerification(String username, String hashedPassword,
            String email, Set<Role> roles) {
        Objects.requireNonNull(email, "email is required");
        User u = of(username, hashedPassword, roles);
        u.email = email;
        u.enabled = false;
        return u;
    }

    /**
     * Cliente do marketplace: mesmo {@code UserEntity}, {@code ROLE_CUSTOMER}, ligado 1:1 ao
     * {@code Customer} do CRM. Nasce habilitado — o cadastro público (Fatia 8) não passa pelo
     * fluxo de verificação de email dos operadores.
     */
    public static User customer(String username, String hashedPassword, String email, Long customerId,
            Set<Role> roles) {
        Objects.requireNonNull(customerId, "customerId is required for a customer account");
        User u = of(username, hashedPassword, roles);
        u.email = email;
        u.userType = UserType.CUSTOMER;
        u.customerId = customerId;
        return u;
    }

    public static User fromGoogle(String googleId, String username, String email, Set<Role> roles) {
        Objects.requireNonNull(googleId, "googleId is required");
        Objects.requireNonNull(username, "username is required");
        Objects.requireNonNull(email, "email is required");
        User u = new User();
        u.googleId = googleId;
        u.username = username;
        u.email = email;
        u.emailVerified = true;
        u.enabled = true;
        u.authProvider = AuthProvider.GOOGLE;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        return u;
    }

    /** Overload pré-Fatia-8: operador comum, sem discriminador de tipo nem customerId. */
    public static User fromPersisted(Long id, String username, String hashedPassword,
            boolean enabled, String email, boolean emailVerified, String pendingEmail,
            String avatarFilename, Instant createdAt, Set<Role> roles,
            String googleId, AuthProvider authProvider) {
        return fromPersisted(id, username, hashedPassword, enabled, email, emailVerified,
                pendingEmail, avatarFilename, createdAt, roles, googleId, authProvider,
                UserType.OPERATOR, null);
    }

    public static User fromPersisted(Long id, String username, String hashedPassword,
            boolean enabled, String email, boolean emailVerified, String pendingEmail,
            String avatarFilename, Instant createdAt, Set<Role> roles,
            String googleId, AuthProvider authProvider, UserType userType, Long customerId) {
        Objects.requireNonNull(id, "id is required for persisted user");
        User u = new User();
        u.id = id;
        u.username = username;
        u.password = hashedPassword;
        u.enabled = enabled;
        u.email = email;
        u.emailVerified = emailVerified;
        u.pendingEmail = pendingEmail;
        u.avatarFilename = avatarFilename;
        u.createdAt = createdAt;
        u.roles = roles != null ? new HashSet<>(roles) : new HashSet<>();
        u.googleId = googleId;
        u.authProvider = authProvider != null ? authProvider : AuthProvider.LOCAL;
        u.userType = userType != null ? userType : UserType.OPERATOR;
        u.customerId = customerId;
        return u;
    }

    // --- Domain operations ---

    public void assignEmail(String email) {
        this.email = email;
    }

    public void changeEmail(String newEmail) {
        Objects.requireNonNull(newEmail, "newEmail is required");
        this.email = newEmail;
        this.emailVerified = false;
    }

    public void changePassword(String hashedPassword) {
        Objects.requireNonNull(hashedPassword, "hashedPassword is required");
        this.password = hashedPassword;
    }

    public void rename(String newUsername) {
        Objects.requireNonNull(newUsername, "newUsername is required");
        this.username = newUsername;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

    public void confirmEmail() {
        this.emailVerified = true;
        this.enabled = true;
    }

    public void setPendingEmail(String email) {
        Objects.requireNonNull(email, "pendingEmail is required");
        this.pendingEmail = email;
    }

    public void applyPendingEmail() {
        Objects.requireNonNull(pendingEmail, "no pending email to apply");
        this.email = this.pendingEmail;
        this.pendingEmail = null;
    }

    public void setAvatar(String avatarFilename) {
        this.avatarFilename = avatarFilename;
    }

    public void clearAvatar() {
        this.avatarFilename = null;
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public void removeRole(Role role) {
        this.roles.removeIf(r -> r.getName().equals(role.getName()));
    }

    /**
     * PLAT-F005 — liga a conta de operador ao seu cadastro de cliente do CRM (quem opera o PDV também
     * compra na loja). A conta de cliente da loja nasce ligada e não troca de cliente.
     */
    public void linkCustomer(Long customerId) {
        Objects.requireNonNull(customerId, "customerId is required");
        if (isCustomer()) throw new CustomerAccountLinkImmutableException(username);
        this.customerId = customerId;
    }

    /** PLAT-F005 — desfaz o vínculo da conta de operador. */
    public void unlinkCustomer() {
        if (isCustomer()) throw new CustomerAccountLinkImmutableException(username);
        this.customerId = null;
    }

    public void linkGoogle(String googleId) {
        Objects.requireNonNull(googleId, "googleId is required");
        this.googleId = googleId;
    }

    // --- Accessors ---

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isEnabled() { return enabled; }
    public String getEmail() { return email; }
    public boolean isEmailVerified() { return emailVerified; }
    public String getPendingEmail() { return pendingEmail; }
    public String getAvatarFilename() { return avatarFilename; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<Role> getRoles() { return roles; }
    public String getGoogleId() { return googleId; }
    public AuthProvider getAuthProvider() { return authProvider; }
    public UserType getUserType() { return userType; }
    public Long getCustomerId() { return customerId; }
    public boolean isCustomer() { return userType == UserType.CUSTOMER; }
}
