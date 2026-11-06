package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.UserForceEmailRequest;
import com.cernecommerce.adapter.in.dtos.request.UserTemporaryPasswordRequest;
import com.cernecommerce.adapter.in.dtos.request.UserTwoFactorRequiredRequest;
import com.cernecommerce.adapter.in.dtos.response.SessionInfoDTO;
import com.cernecommerce.adapter.in.dtos.response.UserSecurityResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.avatar.InvalidAvatarFormatException;
import com.cernecommerce.core.domain.exception.user.SelfTargetNotAllowedException;
import com.cernecommerce.core.domain.exception.user.TargetProtectedException;
import com.cernecommerce.core.ports.in.AvatarUseCase;
import com.cernecommerce.core.ports.in.UserSecurityUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Ações de um admin sobre a segurança da conta de outra pessoa. Toda rota respeita a proteção da conta de
 * dev, e as que pulariam a confirmação de senha da própria conta recusam o próprio usuário.
 */
@RestController
@RequestMapping("/users/{id}")
@Tag(name = "Usuários: segurança", description = "Senha, 2FA, sessões e restauração, pelo admin")
@SecurityRequirement(name = "bearerAuth")
public class UserSecurityController {

    private final UserSecurityUseCase security;
    private final UserUseCase users;
    private final AvatarUseCase avatars;
    private final ApplicationEventPublisher publisher;

    public UserSecurityController(UserSecurityUseCase security, UserUseCase users, AvatarUseCase avatars,
            ApplicationEventPublisher publisher) {
        this.security = security;
        this.users = users;
        this.avatars = avatars;
        this.publisher = publisher;
    }

    @Operation(summary = "Manda o e-mail de redefinição de senha", description = "400 INVITE_EMAIL_REQUIRED sem e-mail.")
    @PostMapping("/password-reset")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> sendPasswordReset(@PathVariable Long id, Authentication auth) {
        requireCanTarget(id, auth);
        return audited(EventType.USER_PASSWORD_RESET_SENT, security.sendPasswordReset(id), auth);
    }

    @Operation(summary = "Define uma senha temporária",
            description = "Obriga a troca no próximo login e encerra as sessões. 400 INVALID_PASSWORD fora da política.")
    @PutMapping("/password")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> setTemporaryPassword(@PathVariable Long id,
            @Valid @RequestBody UserTemporaryPasswordRequest request, Authentication auth) {
        requireOtherAccount(id, auth);
        return audited(EventType.USER_TEMP_PASSWORD_SET,
                security.setTemporaryPassword(id, request.temporaryPassword()), auth);
    }

    @Operation(summary = "Estado de segurança da conta")
    @GetMapping("/security")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public UserSecurityResponseDTO getSecurity(@PathVariable Long id, Authentication auth) {
        requireCanTarget(id, auth);
        return UserSecurityResponseDTO.of(security.getSecurity(id));
    }

    @Operation(summary = "Liga ou desliga a exigência de 2FA", description = "Ao ligar, avisa por e-mail. Idempotente.")
    @PutMapping("/2fa/required")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> setTwoFactorRequired(@PathVariable Long id,
            @Valid @RequestBody UserTwoFactorRequiredRequest request, Authentication auth) {
        requireOtherAccount(id, auth);
        String username = security.setTwoFactorRequired(id, request.required());
        publisher.publishEvent(AuditEvent.of(EventType.USER_2FA_REQUIRED, username,
                Map.of("by", auth.getName(), "required", request.required())));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reseta o 2FA de quem perdeu o celular",
            description = "Apaga a configuração e os códigos de recuperação e encerra as sessões.")
    @DeleteMapping("/2fa")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> resetTwoFactor(@PathVariable Long id, Authentication auth) {
        requireOtherAccount(id, auth);
        return audited(EventType.USER_2FA_RESET, security.resetTwoFactor(id), auth);
    }

    @Operation(summary = "Sessões ativas da conta")
    @GetMapping("/sessions")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public List<SessionInfoDTO> listSessions(@PathVariable Long id, Authentication auth) {
        requireOtherAccount(id, auth);
        return security.listSessions(id).stream().map(SessionInfoDTO::from).toList();
    }

    @Operation(summary = "Encerra todas as sessões da conta")
    @DeleteMapping("/sessions")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> revokeSessions(@PathVariable Long id, Authentication auth) {
        requireOtherAccount(id, auth);
        return audited(EventType.USER_SESSIONS_REVOKED, security.revokeSessions(id), auth);
    }

    @Operation(summary = "Encerra uma sessão da conta", description = "404 se a sessão não for desse usuário.")
    @DeleteMapping("/sessions/{sessionId}")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id, @PathVariable Long sessionId,
            Authentication auth) {
        requireOtherAccount(id, auth);
        return audited(EventType.USER_SESSIONS_REVOKED, security.revokeSession(id, sessionId), auth);
    }

    @Operation(summary = "Troca o e-mail sem confirmação",
            description = "Marca como verificado e avisa o endereço antigo. 409 EMAIL_ALREADY_EXISTS.")
    @PutMapping("/email")
    @PreAuthorize("hasAuthority('USER_SECURITY_MANAGE')")
    public ResponseEntity<Void> forceEmail(@PathVariable Long id, @Valid @RequestBody UserForceEmailRequest request,
            Authentication auth) {
        requireCanTarget(id, auth);
        return audited(EventType.USER_EMAIL_FORCED, security.forceEmail(id, request.email()), auth);
    }

    @Operation(summary = "Troca o avatar da conta", description = "Mesmas regras de /users/me/avatar.")
    @PostMapping("/avatar")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<Map<String, String>> uploadAvatar(@PathVariable Long id,
            @RequestParam("file") MultipartFile file, Authentication auth) {
        requireCanTarget(id, auth);
        if (file == null || file.isEmpty()) {
            throw new InvalidAvatarFormatException();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new InvalidAvatarFormatException();
        }
        String avatarUrl = avatars.upload(security.usernameOf(id), bytes, file.getOriginalFilename());
        return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
    }

    @Operation(summary = "Remove o avatar da conta")
    @DeleteMapping("/avatar")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<Void> deleteAvatar(@PathVariable Long id, Authentication auth) {
        requireCanTarget(id, auth);
        avatars.delete(security.usernameOf(id));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Restaura uma conta excluída",
            description = "A conta volta inativa, sem 2FA, avatar e vínculo. 409 USERNAME_ALREADY_EXISTS ou "
                    + "EMAIL_ALREADY_EXISTS se outra conta pegou o nome ou o e-mail.")
    @PostMapping("/restore")
    @PreAuthorize("hasAuthority('USER_STATUS')")
    public ResponseEntity<Void> restore(@PathVariable Long id, Authentication auth) {
        boolean elevated = AuthUtils.hasAuthority(auth, "DEV_ELEVATED");
        return audited(EventType.USER_RESTORED, security.restore(id, elevated), auth);
    }

    private ResponseEntity<Void> audited(EventType type, String username, Authentication auth) {
        publisher.publishEvent(AuditEvent.of(type, username, Map.of("by", auth.getName())));
        return ResponseEntity.noContent().build();
    }

    private void requireCanTarget(Long id, Authentication auth) {
        if (users.isDevAccount(id) && !AuthUtils.hasAuthority(auth, "DEV_ELEVATED")) {
            throw new TargetProtectedException();
        }
    }

    private void requireOtherAccount(Long id, Authentication auth) {
        requireCanTarget(id, auth);
        if (security.usernameOf(id).equals(auth.getName())) {
            throw new SelfTargetNotAllowedException();
        }
    }
}
