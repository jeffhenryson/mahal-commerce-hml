package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.OffSystemReceiptRequest;
import com.cernecommerce.adapter.in.dtos.response.OffSystemReceiptResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;
import com.cernecommerce.core.ports.in.OffSystemReceiptUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Valor recebido por fora" do caixa: Pix ou cartão que caiu na maquininha ou na conta sem passar pelo
 * sistema. Serve só para a conferência por operadora; não entra no esperado nem no total recebido.
 */
@RestController
@RequestMapping("/pdv/sessions/{id}/off-system-receipts")
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PdvSessionOffSystemReceiptController {

    private static final String CLOSE_ANY_AUTHORITY = "PDV_SESSION_CLOSE_ANY";

    private final OffSystemReceiptUseCase receiptUseCase;
    private final ApplicationEventPublisher publisher;

    public PdvSessionOffSystemReceiptController(OffSystemReceiptUseCase receiptUseCase,
            ApplicationEventPublisher publisher) {
        this.receiptUseCase = receiptUseCase;
        this.publisher = publisher;
    }

    @Operation(summary = "Lança um valor recebido por fora do sistema",
            description = "Com o caixa aberto, o operador dono do caixa; com PDV_SESSION_CLOSE_ANY, em qualquer "
                    + "caixa, aberto ou fechado. method: PIX, DEBITO ou CREDITO. provider opcional "
                    + "(INFINITYPAY, CIELO). Não entra em expectedAmount nem em totalReceived.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lançado",
                    content = @Content(schema = @Schema(implementation = OffSystemReceiptResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Valor, forma ou observação inválidos", content = @Content),
            @ApiResponse(responseCode = "403", description = "SESSION_NOT_OWNED — caixa de outro operador",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content),
            @ApiResponse(responseCode = "409", description = "CASH_REGISTER_SESSION_CLOSED — caixa fechado, sem "
                    + "PDV_SESSION_CLOSE_ANY", content = @Content)
    })
    @PostMapping
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<OffSystemReceiptResponseDTO> register(@PathVariable("id") Long sessionId,
            @Valid @RequestBody OffSystemReceiptRequest request, Authentication authentication) {
        OffSystemReceipt receipt = receiptUseCase.register(sessionId, request.getAmount(), request.getMethod(),
                request.getProvider(), request.getNote(), authentication.getName(),
                AuthUtils.hasAuthority(authentication, CLOSE_ANY_AUTHORITY));
        publisher.publishEvent(AuditEvent.of(EventType.OFF_SYSTEM_RECEIPT_REGISTERED, authentication.getName(),
                details(receipt)));
        return ResponseEntity.status(201).body(OffSystemReceiptResponseDTO.from(receipt));
    }

    @Operation(summary = "Lista os valores recebidos por fora do caixa, mais recentes primeiro")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Sessão não encontrada", content = @Content)
    })
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PDV_READ', 'ORDER_READ')")
    public ResponseEntity<List<OffSystemReceiptResponseDTO>> list(@PathVariable("id") Long sessionId) {
        return ResponseEntity.ok(receiptUseCase.list(sessionId).stream()
                .map(OffSystemReceiptResponseDTO::from).toList());
    }

    @Operation(summary = "Apaga um valor recebido por fora",
            description = "Exclusão lógica (fica quem apagou e quando). Mesma regra de acesso do lançamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Apagado"),
            @ApiResponse(responseCode = "403", description = "SESSION_NOT_OWNED — caixa de outro operador",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "OFF_SYSTEM_RECEIPT_NOT_FOUND ou sessão não encontrada",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "CASH_REGISTER_SESSION_CLOSED — caixa fechado, sem "
                    + "PDV_SESSION_CLOSE_ANY", content = @Content)
    })
    @DeleteMapping("/{receiptId}")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable("id") Long sessionId, @PathVariable("receiptId") Long receiptId,
            Authentication authentication) {
        OffSystemReceipt receipt = receiptUseCase.delete(sessionId, receiptId, authentication.getName(),
                AuthUtils.hasAuthority(authentication, CLOSE_ANY_AUTHORITY));
        publisher.publishEvent(AuditEvent.of(EventType.OFF_SYSTEM_RECEIPT_DELETED, authentication.getName(),
                details(receipt)));
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> details(OffSystemReceipt receipt) {
        Map<String, Object> details = new HashMap<>();
        details.put("sessionId", receipt.sessionId());
        details.put("receiptId", receipt.id());
        details.put("amount", receipt.amount());
        details.put("method", receipt.method().name());
        // AuditEvent.of copia com Map.copyOf, que recusa valor nulo.
        if (receipt.provider() != null) {
            details.put("provider", receipt.provider().name());
        }
        if (receipt.note() != null) {
            details.put("note", receipt.note());
        }
        return details;
    }
}
