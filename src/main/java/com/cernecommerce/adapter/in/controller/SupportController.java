package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.CreateBugReportRequest;
import com.cernecommerce.adapter.in.dtos.response.BugReportResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.support.BugReport;
import com.cernecommerce.core.ports.in.BugReportUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Relato de bugs enviado pelo botão "Reportar bug" do admin (mahal-admin).
 */
@RestController
@RequestMapping("/support")
@SecurityRequirement(name = "bearerAuth")
public class SupportController {

    private final BugReportUseCase bugReportUseCase;
    private final ApplicationEventPublisher publisher;

    public SupportController(BugReportUseCase bugReportUseCase, ApplicationEventPublisher publisher) {
        this.bugReportUseCase = bugReportUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Registra um relato de bug enviado pelo usuário autenticado")
    @PostMapping("/bug-reports")
    public ResponseEntity<BugReportResponseDTO> createBugReport(@Valid @RequestBody CreateBugReportRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgentHeader,
            Authentication authentication) {
        String userAgent = request.getUserAgent() != null && !request.getUserAgent().isBlank()
                ? request.getUserAgent()
                : userAgentHeader;
        BugReport created = bugReportUseCase.createBugReport(authentication.getName(), request.getTitle(),
                request.getDescription(), request.getPageUrl(), userAgent);
        // Título e descrição vão no evento para o alerta dos devs (DevAlertPort) sair sem reler o relato.
        Map<String, Object> details = new HashMap<>();
        details.put("bugReportId", String.valueOf(created.id()));
        details.put("title", created.title());
        putIfPresent(details, "description", abbreviate(created.description(), 1000));
        putIfPresent(details, "pageUrl", created.pageUrl());
        putIfPresent(details, "userAgent", created.userAgent());
        publisher.publishEvent(AuditEvent.of(EventType.BUG_REPORT_CREATED, authentication.getName(), details));
        return ResponseEntity.created(URI.create("/support/bug-reports/" + created.id()))
                .body(BugReportResponseDTO.from(created));
    }

    private static void putIfPresent(Map<String, Object> details, String key, String value) {
        if (value != null && !value.isBlank()) {
            details.put(key, value);
        }
    }

    private static String abbreviate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
