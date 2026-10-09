package com.cernecommerce.infra.handler;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.*;
import com.cernecommerce.core.domain.exception.auth.AccountDisabledException;
import com.cernecommerce.core.domain.exception.auth.AccountLockedException;
import com.cernecommerce.core.domain.exception.auth.DevChallengeExpiredException;
import com.cernecommerce.core.domain.exception.auth.InvalidPasswordException;
import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.exception.auth.TotpCodeRequiredException;
import com.cernecommerce.core.domain.exception.auth.TotpNotConsecutiveException;
import com.cernecommerce.core.domain.exception.auth.TotpSetupRequiredException;
import com.cernecommerce.core.domain.exception.avatar.AvatarTooLargeException;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.exception.ModuleDisabledException;
import com.cernecommerce.core.domain.exception.auth.InvalidRefreshTokenException;
import com.cernecommerce.core.domain.exception.auth.InvalidTotpCodeException;
import com.cernecommerce.core.domain.exception.auth.PasswordResetTokenExpiredException;
import com.cernecommerce.core.domain.exception.auth.PasswordResetTokenNotFoundException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenAlreadyUsedException;
import com.cernecommerce.core.domain.exception.auth.RefreshTokenExpiredException;
import com.cernecommerce.core.domain.exception.auth.SessionNotFoundException;
import com.cernecommerce.core.domain.exception.auth.TotpAlreadyEnabledException;
import com.cernecommerce.core.domain.exception.auth.TotpChallengeExpiredException;
import com.cernecommerce.core.domain.exception.auth.TotpNotEnabledException;
import com.cernecommerce.core.domain.exception.email.EmailAlreadyVerifiedException;
import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeExpiredException;
import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.cernecommerce.core.domain.exception.ratelimit.RateLimitExceededException;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import org.springframework.context.ApplicationEventPublisher;

import com.cernecommerce.core.domain.model.notification.NotificationType;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testa cada mapeamento de exceção do {@link GlobalExceptionHandler} de forma isolada,
 * sem subir contexto Spring — instancia o handler diretamente.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest req;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "lockoutDurationMinutes", 15L);

        req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/test");
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @Test
    void userNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUserNotFound(new UserNotFoundException(1L), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void roleNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRoleNotFound(new RoleNotFoundException("ROLE_X"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("ROLE_NOT_FOUND");
    }

    @Test
    void permissionNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handlePermissionNotFound(new PermissionNotFoundException("PERM_X"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("PERMISSION_NOT_FOUND");
    }

    // ── 409 Conflict ─────────────────────────────────────────────────────────

    @Test
    void usernameAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUsernameExists(new UsernameAlreadyExistsException("alice"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void emailAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleEmailExists(new EmailAlreadyExistsException("a@b.com"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void emailAlreadyVerified_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleEmailAlreadyVerified(new EmailAlreadyVerifiedException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_ALREADY_VERIFIED");
    }

    @Test
    void roleAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRoleExists(new RoleAlreadyExistsException("ROLE_X"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("ROLE_ALREADY_EXISTS");
    }

    // ── 429 Too Many Requests ─────────────────────────────────────────────────

    @Test
    void accountLocked_returns429_withRetryAfterHeader() {
        ResponseEntity<ApiError> resp = handler.handleAccountLocked(new AccountLockedException("bloqueado"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(resp.getHeaders().getFirst("Retry-After")).isEqualTo("900"); // 15min * 60
    }

    @Test
    void rateLimitExceeded_returns429_withRetryAfterHeader() {
        ResponseEntity<ApiError> resp = handler.handleRateLimitExceeded(
                new RateLimitExceededException("crm-export", 3600), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(resp.getBody().errorCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(resp.getHeaders().getFirst("Retry-After")).isEqualTo("3600");
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────

    @Test
    void emailVerificationCodeNotFound_returns400() {
        ResponseEntity<ApiError> resp = handler.handleVerificationCodeNotFound(
                new EmailVerificationCodeNotFoundException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VERIFICATION_CODE_INVALID");
    }

    @Test
    void emailVerificationCodeExpired_returns400() {
        ResponseEntity<ApiError> resp = handler.handleVerificationCodeExpired(
                new EmailVerificationCodeExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VERIFICATION_CODE_EXPIRED");
    }

    @Test
    void invalidPassword_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleInvalidPassword(new InvalidPasswordException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_PASSWORD");
    }

    @Test
    void illegalArgument_returns400() {
        ResponseEntity<ApiError> resp = handler.handleIllegalArgument(new IllegalArgumentException("bad"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("BAD_REQUEST");
    }

    /**
     * Sem handler dedicado, um {@code @RequestParam} obrigatório ausente cairia no catch-all de
     * {@link Exception} e viraria 500 (EST-F017).
     */
    @Test
    void missingRequestParameter_returns400_namingTheParameter() {
        ResponseEntity<ApiError> resp = handler.handleMissingParam(
                new org.springframework.web.bind.MissingServletRequestParameterException("sku", "String"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(resp.getBody().message()).contains("sku");
    }

    // ── 401 Unauthorized ─────────────────────────────────────────────────────

    @Test
    void invalidRefreshToken_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleInvalidRefreshToken(new InvalidRefreshTokenException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @Test
    void refreshTokenReused_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRefreshTokenReuse(new RefreshTokenAlreadyUsedException("alice"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("REFRESH_TOKEN_REUSED");
    }

    @Test
    void refreshTokenExpired_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleRefreshTokenExpired(new RefreshTokenExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("REFRESH_TOKEN_EXPIRED");
    }

    // ── 503 Service Unavailable ───────────────────────────────────────────────

    @Test
    void emailDelivery_returns503_withCode() {
        ResponseEntity<ApiError> resp = handler.handleEmailDelivery(new EmailDeliveryException("falhou"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_DELIVERY_FAILED");
    }

    // ── 500 Internal Server Error ─────────────────────────────────────────────

    @Test
    void unexpectedException_returns500_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUnexpected(new RuntimeException("boom"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().errorCode()).isEqualTo("INTERNAL_ERROR");
    }

    // ── 401 Unauthorized — auth ───────────────────────────────────────────────

    @Test
    void disabled_returns401_withoutAccountDisclosure() {
        ResponseEntity<ApiError> resp = handler.handleDisabled(new DisabledException("disabled"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(resp.getBody().message()).isEqualTo("Credenciais inválidas");
    }

    @Test
    void totpChallengeExpired_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleTotpChallengeExpired(new TotpChallengeExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("TOTP_CHALLENGE_EXPIRED");
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────

    @Test
    void accessDenied_returns403_withCode() {
        ResponseEntity<ApiError> resp = handler.handleAccessDenied(new AccessDeniedException("forbidden"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().errorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void accessDenied_publishes_audit_event() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ReflectionTestUtils.setField(handler, "publisher", publisher);

        handler.handleAccessDenied(new AccessDeniedException("forbidden"), req);

        verify(publisher).publishEvent(any(AuditEvent.class));
    }

    @Test
    void accessDenied_without_publisher_does_not_throw() {
        // publisher é null quando não injetado (teste sem contexto Spring)
        ReflectionTestUtils.setField(handler, "publisher", null);
        ResponseEntity<ApiError> resp = handler.handleAccessDenied(new AccessDeniedException("forbidden"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    // ── 404 Not Found — session ───────────────────────────────────────────────

    @Test
    void sessionNotFound_returns404_withCode() {
        ResponseEntity<ApiError> resp = handler.handleSessionNotFound(new SessionNotFoundException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().errorCode()).isEqualTo("SESSION_NOT_FOUND");
    }

    // ── 400 Bad Request — TOTP ────────────────────────────────────────────────

    @Test
    void invalidTotpCode_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleInvalidTotpCode(new InvalidTotpCodeException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_TOTP_CODE");
    }

    @Test
    void totpNotEnabled_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleTotpNotEnabled(new TotpNotEnabledException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("TOTP_NOT_ENABLED");
    }

    // ── 409 Conflict — TOTP ───────────────────────────────────────────────────

    @Test
    void totpAlreadyEnabled_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleTotpAlreadyEnabled(new TotpAlreadyEnabledException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("TOTP_ALREADY_ENABLED");
    }

    // ── 409 Conflict — permission ─────────────────────────────────────────────

    @Test
    void permissionAlreadyExists_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handlePermissionExists(new PermissionAlreadyExistsException("USER_READ"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("PERMISSION_ALREADY_EXISTS");
    }

    // ── 400 Bad Request — password reset ─────────────────────────────────────

    @Test
    void passwordResetTokenNotFound_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handlePasswordResetTokenNotFound(new PasswordResetTokenNotFoundException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("PASSWORD_RESET_TOKEN_INVALID");
    }

    @Test
    void passwordResetTokenExpired_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handlePasswordResetTokenExpired(new PasswordResetTokenExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("PASSWORD_RESET_TOKEN_EXPIRED");
    }

    // ── 400 Bad Request — validation ──────────────────────────────────────────

    @Test
    void methodArgumentNotValid_returns400_withFieldMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "username", "must not be blank")));

        ResponseEntity<ApiError> resp = handler.handleValidation(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().message()).contains("must not be blank");
    }

    @Test
    void constraintViolation_returns400_withValidationErrorCode() {
        ResponseEntity<ApiError> resp = handler.handleConstraintViolation(
                new ConstraintViolationException("invalid", Set.of()), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void unreadableBody_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleUnreadableBody(
                new HttpMessageNotReadableException("bad body", new MockHttpInputMessage(new byte[0])), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("UNREADABLE_BODY");
    }

    // ── 401 Unauthorized — account disabled ──────────────────────────────────

    @Test
    void accountDisabled_returns401_withoutAccountDisclosure() {
        ResponseEntity<ApiError> resp = handler.handleAccountDisabled(new AccountDisabledException("alice"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(resp.getBody().message()).isEqualTo("Credenciais inválidas");
    }

    // ── 403 Forbidden — TOTP setup required ──────────────────────────────────

    @Test
    void totpSetupRequired_returns403_withCode() {
        ResponseEntity<ApiError> resp = handler.handleTotpSetupRequired(new TotpSetupRequiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().errorCode()).isEqualTo("TOTP_SETUP_REQUIRED");
    }

    // ── 503 Service Unavailable — module disabled ─────────────────────────────

    @Test
    void moduleDisabled_returns503_withCode() {
        ResponseEntity<ApiError> resp = handler.handleModuleDisabled(new ModuleDisabledException("roles"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody().errorCode()).isEqualTo("MODULE_DISABLED");
    }

    // ── 400 Bad Request — TOTP DEV ────────────────────────────────────────────

    @Test
    void totpCodeRequired_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleTotpCodeRequired(new TotpCodeRequiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("TOTP_CODE_REQUIRED");
    }

    @Test
    void totpNotConsecutive_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleTotpNotConsecutive(new TotpNotConsecutiveException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("TOTP_NOT_CONSECUTIVE");
    }

    // ── 410 Gone — dev challenge expired ─────────────────────────────────────

    @Test
    void devChallengeExpired_returns410_withCode() {
        ResponseEntity<ApiError> resp = handler.handleDevChallengeExpired(new DevChallengeExpiredException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.GONE);
        assertThat(resp.getBody().errorCode()).isEqualTo("DEV_CHALLENGE_EXPIRED");
    }

    // ── 401 Unauthorized — OAuth ──────────────────────────────────────────────

    @Test
    void oauthTokenInvalid_returns401_withCode() {
        ResponseEntity<ApiError> resp = handler.handleOAuthTokenInvalid(new OAuthTokenInvalidException("bad token"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().errorCode()).isEqualTo("OAUTH_TOKEN_INVALID");
    }

    // ── 400 Bad Request — avatar ──────────────────────────────────────────────

    @Test
    void avatarTooLarge_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleAvatarTooLarge(new AvatarTooLargeException(2_097_152L), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("AVATAR_TOO_LARGE");
    }

    @Test
    void invalidAvatarFormat_returns400_withCode() {
        ResponseEntity<ApiError> resp = handler.handleInvalidAvatarFormat(new InvalidAvatarFormatException(), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_AVATAR_FORMAT");
    }

    // ── 400 Bad Request — type mismatch ──────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void typeMismatch_enum_returns400_withValidValues() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("type");
        when(ex.getRequiredType()).thenReturn((Class) NotificationType.class);

        ResponseEntity<ApiError> resp = handler.handleTypeMismatch(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_ENUM_VALUE");
        assertThat(resp.getBody().message()).contains("type");
        assertThat(resp.getBody().message()).contains("PASSWORD_CHANGED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void typeMismatch_non_enum_returns400_withParamName() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        when(ex.getRequiredType()).thenReturn((Class) Long.class);

        ResponseEntity<ApiError> resp = handler.handleTypeMismatch(ex, req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().errorCode()).isEqualTo("INVALID_ENUM_VALUE");
        assertThat(resp.getBody().message()).contains("id");
    }

    // ── path e timestamp na resposta ─────────────────────────────────────────

    @Test
    void error_response_contains_path_and_timestamp() {
        when(req.getRequestURI()).thenReturn("/api/v1/users/99");
        ResponseEntity<ApiError> resp = handler.handleUserNotFound(new UserNotFoundException(99L), req);
        assertThat(resp.getBody().path()).isEqualTo("/api/v1/users/99");
        assertThat(resp.getBody().timestamp()).isNotNull();
    }

    // ── PLAT-C055 — violação de unicidade de usuário vira o código específico ────────────────

    private static org.springframework.dao.DataIntegrityViolationException uniqueViolation(String constraint) {
        var cause = new org.hibernate.exception.ConstraintViolationException(
                "duplicate key", new java.sql.SQLException("duplicate key value violates unique constraint"), constraint);
        return new org.springframework.dao.DataIntegrityViolationException("could not execute statement", cause);
    }

    @Test
    void dataIntegrity_onUsernameConstraint_returnsUsernameAlreadyExists() {
        ResponseEntity<ApiError> resp = handler.handleDataIntegrityViolation(uniqueViolation("uk_user_username"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("USERNAME_ALREADY_EXISTS");
    }

    @Test
    void dataIntegrity_onEmailConstraint_returnsEmailAlreadyExists() {
        ResponseEntity<ApiError> resp = handler.handleDataIntegrityViolation(uniqueViolation("UK_USER_EMAIL"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    /** EST-F033: a corrida entre dois cadastros da mesma lata responde igual à checagem do service. */
    @Test
    void dataIntegrity_onOpenPackageConstraint_returnsOpenPackageAlreadyOpen() {
        ResponseEntity<ApiError> resp = handler.handleDataIntegrityViolation(
                uniqueViolation("uk_open_package_sku_warehouse_open"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("OPEN_PACKAGE_ALREADY_OPEN");
    }

    @Test
    void dataIntegrity_onOtherConstraint_keepsGenericCode() {
        ResponseEntity<ApiError> resp = handler.handleDataIntegrityViolation(uniqueViolation("uk_products_sku"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    void dataIntegrity_withoutConstraintName_keepsGenericCode() {
        ResponseEntity<ApiError> resp = handler.handleDataIntegrityViolation(
                new org.springframework.dao.DataIntegrityViolationException("boom"), req);
        assertThat(resp.getBody().errorCode()).isEqualTo("DATA_INTEGRITY_VIOLATION");
    }

    @Test
    void customerAccountRolesImmutable_returns409_withCode() {
        ResponseEntity<ApiError> resp = handler.handleCustomerAccountRolesImmutable(
                new com.cernecommerce.core.domain.exception.user.CustomerAccountRolesImmutableException("cli@x.com"), req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().errorCode()).isEqualTo("CUSTOMER_ACCOUNT_ROLES_IMMUTABLE");
    }
}
