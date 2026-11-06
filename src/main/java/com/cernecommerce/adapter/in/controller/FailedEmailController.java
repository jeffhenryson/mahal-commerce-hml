package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.notification.FailedEmail;
import com.cernecommerce.core.ports.in.FailedEmailUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Só a equipe técnica: o conteúdo pode ter dado de cliente. */
@RestController
@RequestMapping("/failed-emails/{id}")
@Tag(name = "E-mails que falharam", description = "Ver e reenviar o e-mail que o provedor recusou")
@SecurityRequirement(name = "bearerAuth")
public class FailedEmailController {

    private final FailedEmailUseCase useCase;

    public FailedEmailController(FailedEmailUseCase useCase) {
        this.useCase = useCase;
    }

    @Operation(summary = "E-mail que falhou, com assunto e HTML", description = "As falhas expiram em 30 dias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "FAILED_EMAIL_NOT_FOUND", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_DEV')")
    public FailedEmail get(@PathVariable Long id) {
        return useCase.get(id);
    }

    @Operation(summary = "Reenvia com o mesmo assunto e HTML",
            description = "Responde 200 mesmo se o provedor recusar de novo: veja lastResendStatus.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content),
            @ApiResponse(responseCode = "404", description = "FAILED_EMAIL_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "429", description = "Reenviado há menos de 30 segundos", content = @Content)
    })
    @PostMapping("/resend")
    @PreAuthorize("hasAuthority('ROLE_DEV')")
    public FailedEmail resend(@PathVariable Long id) {
        return useCase.resend(id);
    }
}
