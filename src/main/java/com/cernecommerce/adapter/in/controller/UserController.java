package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.UserDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ChangePasswordRequest;
import com.cernecommerce.adapter.in.dtos.request.CreateUserRequest;
import com.cernecommerce.adapter.in.dtos.request.UserUpdateRequest;
import com.cernecommerce.adapter.in.dtos.response.UserProfileDTO;
import com.cernecommerce.adapter.in.dtos.response.UserResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.UpdateProfileResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/users")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class UserController {

    private final UserUseCase useCase;
    private final UserDTOConverter converter;
    private final ApplicationEventPublisher publisher;

    public UserController(UserUseCase useCase, UserDTOConverter converter, ApplicationEventPublisher publisher) {
        this.useCase = useCase;
        this.converter = converter;
        this.publisher = publisher;
    }

    @Operation(summary = "Cria um novo usuário",
            description = "Sem password, cria por convite: o usuário recebe no e-mail um link (48h) para definir a senha.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Criado", content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "INVITE_EMAIL_REQUIRED — convite sem e-mail", content = @Content),
            @ApiResponse(responseCode = "409", description = "Username já existe", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<UserResponseDTO> create(@Valid @RequestBody CreateUserRequest request,
            Authentication auth) {
        // Sem esta checagem, USER_CREATE sozinho bastaria pra criar um usuário já nascendo
        // ROLE_DEV, contornando a mesma barreira elevada que assignRole já impõe logo abaixo —
        // duas portas para o mesmo cofre não podem ter fechaduras diferentes.
        requireDevElevationIfAssigningDevRole(request.getRoles(), auth);
        boolean invite = request.getPassword() == null || request.getPassword().isBlank();
        User created = invite
                ? useCase.inviteUser(request.getUsername(), request.getEmail(), request.getRoles())
                : useCase.createUser(request.getUsername(), request.getPassword(), request.getEmail(), request.getRoles());
        publisher.publishEvent(AuditEvent.of(EventType.USER_CREATED, created.getUsername(),
                Map.of("invited", invite, "createdBy", auth == null ? "system" : auth.getName())));
        UserResponseDTO body = converter.toResponse(created);
        return ResponseEntity.created(URI.create("/users/" + body.getId())).body(body);
    }

    @Operation(summary = "Atribui uma role ao usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Atribuída"),
            @ApiResponse(responseCode = "404", description = "Usuário não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conta de cliente da loja — roles não administráveis (CUSTOMER_ACCOUNT_ROLES_IMMUTABLE)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/{username}/roles/{roleName}")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public ResponseEntity<Void> assignRole(@PathVariable String username, @PathVariable String roleName,
            Authentication auth) {
        requireDevElevationIfAssigningDevRole(java.util.List.of(roleName), auth);
        useCase.assignRole(username, roleName);
        publisher.publishEvent(AuditEvent.of(EventType.USER_ROLE_ASSIGNED, username, Map.of("role", roleName)));
        return ResponseEntity.noContent().build();
    }

    /**
     * ROLE_DEV só pode ser concedido — na criação ou por atribuição avulsa — por quem já tem
     * DEV_ROLE_MANAGE com token elevado (DEV_ELEVATED). Sem essa barreira nos dois pontos de
     * entrada, um operador com só USER_CREATE/USER_ROLE_ASSIGN conseguiria se autopromover.
     */
    private void requireDevElevationIfAssigningDevRole(java.util.List<String> roles, Authentication auth) {
        if (roles == null || !roles.contains("ROLE_DEV")) return;
        boolean hasDevRoleManage = auth.getAuthorities().stream()
                .anyMatch(a -> "DEV_ROLE_MANAGE".equals(a.getAuthority()));
        boolean hasDevElevated = auth.getAuthorities().stream()
                .anyMatch(a -> "DEV_ELEVATED".equals(a.getAuthority()));
        if (!hasDevRoleManage || !hasDevElevated) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Atribuição de ROLE_DEV requer DEV_ROLE_MANAGE com token elevado");
        }
    }

    @Operation(summary = "Remove uma role do usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Removida"),
            @ApiResponse(responseCode = "404", description = "Usuário ou role não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Conta de cliente da loja — roles não administráveis (CUSTOMER_ACCOUNT_ROLES_IMMUTABLE)", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/{username}/roles/{roleName}")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public ResponseEntity<Void> removeRole(@PathVariable String username, @PathVariable String roleName) {
        useCase.removeRole(username, roleName);
        publisher.publishEvent(AuditEvent.of(EventType.USER_ROLE_REMOVED, username, Map.of("role", roleName)));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Retorna o perfil do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserProfileDTO.class))),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<UserProfileDTO> me(Authentication authentication) {
        User user = useCase.findByUsername(authentication.getName())
                .orElseThrow(() -> new UserNotFoundException(authentication.getName()));
        return ResponseEntity.ok(converter.toProfile(user));
    }

    @Operation(summary = "Troca a senha do usuário autenticado")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Senha alterada"),
            @ApiResponse(responseCode = "400", description = "Senha atual incorreta ou código 2FA inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @PutMapping("/me/password")
    public ResponseEntity<Void> changeOwnPassword(@Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        useCase.changeOwnPassword(authentication.getName(),
                request.getCurrentPassword(), request.getNewPassword(),
                request.getTotpCode(), request.isRevokeOtherSessions());
        publisher.publishEvent(AuditEvent.of(EventType.USER_PASSWORD_CHANGED, authentication.getName()));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Atualiza os dados do próprio perfil (username e/ou email)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = UserProfileDTO.class))),
            @ApiResponse(responseCode = "409", description = "Username ou email já existe", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @PatchMapping("/me")
    public ResponseEntity<UserProfileDTO> updateOwnProfile(@Valid @RequestBody UserUpdateRequest request,
            Authentication authentication) {
        UpdateProfileResult result = useCase.updateOwnProfile(
                authentication.getName(), request.getUsername(), request.getEmail(), request.getCurrentPassword());
        if (result.emailChangePending()) {
            publisher.publishEvent(AuditEvent.of(EventType.EMAIL_CHANGE_REQUESTED, result.user().getUsername()));
        } else {
            publisher.publishEvent(AuditEvent.of(EventType.USER_UPDATED, result.user().getUsername()));
        }
        return ResponseEntity.ok(converter.toProfile(result.user()));
    }

    @Operation(summary = "Busca usuário por id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<UserResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(useCase.getUserById(id)));
    }

    @Operation(summary = "Lista operadores paginado com filtros opcionais (contas de cliente da loja não entram)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<PageResult<UserResponseDTO>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @io.swagger.v3.oas.annotations.Parameter(description = "Campo de ordenação: id, username, email, enabled, createdAt")
            @RequestParam(defaultValue = "id") String sortBy,
            @io.swagger.v3.oas.annotations.Parameter(description = "Direção: asc ou desc")
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication auth) {
        Set<String> excludeRoles = AuthUtils.isDevAccess(auth) ? Set.of() : Set.of("ROLE_DEV");
        PageResult<User> result = useCase.findFiltered(search, enabled, sortBy, sortDir, page, Math.min(size, 100), excludeRoles);
        PageResult<UserResponseDTO> response = new PageResult<>(
                result.content().stream().map(converter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Reenvia o convite do usuário",
            description = "Invalida o link anterior e manda um novo (48h) para o e-mail do usuário.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Reenviado"),
            @ApiResponse(responseCode = "400", description = "INVITE_EMAIL_REQUIRED — usuário sem e-mail", content = @Content),
            @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PostMapping("/{id}/invite/resend")
    @PreAuthorize("hasAuthority('USER_CREATE')")
    public ResponseEntity<Void> resendInvite(@PathVariable Long id, Authentication auth) {
        useCase.resendInvite(id);
        publisher.publishEvent(AuditEvent.of(EventType.USER_UPDATED, auth == null ? "system" : auth.getName(),
                Map.of("userId", id, "action", "invite-resent")));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Remove usuário por id")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Removido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        String username = useCase.deleteUser(id);
        publisher.publishEvent(AuditEvent.of(EventType.USER_DELETED, username));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Desativa conta de usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Desativado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('USER_STATUS')")
    public ResponseEntity<Void> disable(@PathVariable Long id) {
        String username = useCase.setUserEnabled(id, false);
        publisher.publishEvent(AuditEvent.of(EventType.USER_DISABLED, username));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Reativa conta de usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Reativado"),
            @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PutMapping("/{id}/enable")
    @PreAuthorize("hasAuthority('USER_STATUS')")
    public ResponseEntity<Void> enable(@PathVariable Long id) {
        String username = useCase.setUserEnabled(id, true);
        publisher.publishEvent(AuditEvent.of(EventType.USER_ENABLED, username));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Atualiza dados básicos do usuário")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Atualizado", content = @Content(schema = @Schema(implementation = UserResponseDTO.class))),
            @ApiResponse(responseCode = "404", description = "Não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Username já existe", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        UpdateProfileResult result = useCase.updateUser(id, request.getUsername(), request.getEmail());
        if (result.emailChangePending()) {
            publisher.publishEvent(AuditEvent.of(EventType.EMAIL_CHANGE_REQUESTED, result.user().getUsername()));
        } else {
            publisher.publishEvent(AuditEvent.of(EventType.USER_UPDATED, result.user().getUsername()));
        }
        return ResponseEntity.ok(converter.toResponse(result.user()));
    }
}
