package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.ResendVerificationRequest;
import com.cernecommerce.adapter.in.dtos.request.VerifyEmailRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.auth.RegistrationDisabledException;
import com.cernecommerce.core.ports.in.UserUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class RegistrationController {

        private final UserUseCase userUseCase;
        private final ApplicationEventPublisher publisher;

        public RegistrationController(UserUseCase userUseCase, ApplicationEventPublisher publisher) {
                this.userUseCase = userUseCase;
                this.publisher = publisher;
        }

        @Operation(summary = "Auto-cadastro desativado — usuários são criados por dev/admin em Configurações › Usuários")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "403", description = "REGISTRATION_DISABLED", content = @Content)
        })
        @PostMapping("/register")
        ResponseEntity<Void> register() {
                throw new RegistrationDisabledException();
        }

        @Operation(summary = "Confirma email com código recebido por email")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Email confirmado — conta ativada"),
                        @ApiResponse(responseCode = "400", description = "Código inválido ou expirado", content = @Content)
        })
        @PostMapping("/verify-email")
        ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
                String username = userUseCase.verifyEmail(request.getCode());
                publisher.publishEvent(AuditEvent.of(EventType.USER_EMAIL_VERIFIED, username));
                return ResponseEntity.noContent().build();
        }

        @Operation(summary = "Reenvia código de verificação para o email informado", description = "Sempre retorna 204 — não revela se o email está cadastrado.")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "204", description = "Código reenviado (ou email não encontrado — resposta idêntica por segurança)")
        })
        @PostMapping("/resend-verification")
        ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
                try {
                        userUseCase.resendVerification(request.getEmail());
                } catch (RuntimeException ignored) {
                        // Security: always 204 — never reveal whether email is registered,
                        // verified, on cooldown, or whether delivery failed.
                }
                return ResponseEntity.noContent().build();
        }
}
