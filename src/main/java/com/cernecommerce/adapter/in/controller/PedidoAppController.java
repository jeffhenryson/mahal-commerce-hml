package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.PedidoAppDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.PedidoAppAceiteRequest;
import com.cernecommerce.adapter.in.dtos.request.PedidoAppAssumirRequest;
import com.cernecommerce.adapter.in.dtos.request.PedidoAppRecusaRequest;
import com.cernecommerce.adapter.in.dtos.response.PedidoAppResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;
import com.cernecommerce.core.ports.in.PedidoAppUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Fila "Pedidos do app" do caixa (ECM-F010).
 *
 * <p>O pedido feito no app chega como {@code PENDENTE}, com aviso em tempo real
 * ({@code PEDIDO_APP} pelo SSE de notificações). O operador aceita no próprio caixa aberto — na
 * entrega, informando a taxa —, separa ou despacha, e recebe pela liquidação de sempre
 * ({@code POST /pdv/sessions/{id}/orders/{orderId}/settle}), que passou a exigir o aceite.</p>
 */
@RestController
@RequestMapping("/pdv")
@Tag(name = "PDV — Pedidos do app", description = "Fila, aceite, separação e despacho dos pedidos feitos no app")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class PedidoAppController {

    private final PedidoAppUseCase pedidoAppUseCase;
    private final PedidoAppDTOConverter converter;
    private final ApplicationEventPublisher publisher;

    public PedidoAppController(PedidoAppUseCase pedidoAppUseCase, PedidoAppDTOConverter converter,
            ApplicationEventPublisher publisher) {
        this.pedidoAppUseCase = pedidoAppUseCase;
        this.converter = converter;
        this.publisher = publisher;
    }

    @Operation(summary = "Fila de pedidos do app, do mais antigo ao mais novo",
            description = "Sem status: PENDENTE, ACEITO, PRONTO e SAIU_PARA_ENTREGA — tudo que ainda pede ação. "
                    + "sessionId restringe aos aceitos por aquele caixa.")
    @GetMapping("/app-orders")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<PedidoAppResponseDTO>> listar(
            @RequestParam(required = false) Set<AtendimentoStatus> status,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(converter.toResponse(pedidoAppUseCase.listar(status, sessionId, page, size)));
    }

    @Operation(summary = "Pedidos do app que este caixa atende",
            description = "O \"aparece no caixa aberto\": os aceitos ou assumidos por esta sessão, ainda em andamento.")
    @GetMapping("/sessions/{id}/app-orders")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PageResult<PedidoAppResponseDTO>> listarDoCaixa(@PathVariable("id") Long sessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(converter.toResponse(pedidoAppUseCase.listar(null, sessionId, page, size)));
    }

    @Operation(summary = "Um pedido do app")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Não é pedido do app", content = @Content)
    })
    @GetMapping("/app-orders/{orderId}")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<PedidoAppResponseDTO> buscar(@PathVariable Long orderId) {
        return ResponseEntity.ok(converter.toResponse(pedidoAppUseCase.buscar(orderId)));
    }

    @Operation(summary = "Aceita o pedido no próprio caixa aberto",
            description = "Na ENTREGA, deliveryFee é obrigatória (zero vale) e entra no total a pagar; na RETIRADA, "
                    + "proibida. O cliente recebe e-mail com o total.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aceito"),
            @ApiResponse(responseCode = "400", description = "Taxa ausente na entrega, ou presente na retirada", content = @Content),
            @ApiResponse(responseCode = "403", description = "O caixa é de outro operador", content = @Content),
            @ApiResponse(responseCode = "409", description = "Caixa fechado, ou pedido que não está PENDENTE", content = @Content)
    })
    @PostMapping("/app-orders/{orderId}/aceitar")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<PedidoAppResponseDTO> aceitar(@PathVariable Long orderId,
            @Valid @RequestBody PedidoAppAceiteRequest request, Authentication authentication) {
        return ResponseEntity.ok(converter.toResponse(pedidoAppUseCase.aceitar(orderId, request.getSessionId(),
                request.getDeliveryFee(), authentication.getName())));
    }

    @Operation(summary = "Assume no próprio caixa um pedido aceito por outro",
            description = "Só quando o caixa que aceitou já fechou, ou é do mesmo operador.")
    @PostMapping("/app-orders/{orderId}/assumir")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<PedidoAppResponseDTO> assumir(@PathVariable Long orderId,
            @Valid @RequestBody PedidoAppAssumirRequest request, Authentication authentication) {
        return ResponseEntity.ok(converter.toResponse(
                pedidoAppUseCase.assumir(orderId, request.getSessionId(), authentication.getName())));
    }

    @Operation(summary = "Recusa o pedido: cancela e devolve o estoque reservado",
            description = "Vale até o pagamento. O motivo vai ao cliente.")
    @PostMapping("/app-orders/{orderId}/recusar")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<PedidoAppResponseDTO> recusar(@PathVariable Long orderId,
            @Valid @RequestBody PedidoAppRecusaRequest request, Authentication authentication) {
        PedidoAppUseCase.PedidoApp recusado = pedidoAppUseCase.recusar(orderId, request.getMotivo(),
                authentication.getName());
        Map<String, Object> details = new HashMap<>();
        details.put("orderId", orderId);
        details.put("origin", "PEDIDO_APP_RECUSADO");
        details.put("reason", request.getMotivo());
        publisher.publishEvent(AuditEvent.of(EventType.ORDER_CANCELLED, authentication.getName(), details));
        return ResponseEntity.ok(converter.toResponse(recusado));
    }

    @Operation(summary = "Retirada separada, esperando o cliente")
    @PostMapping("/app-orders/{orderId}/pronto")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<PedidoAppResponseDTO> pronto(@PathVariable Long orderId, Authentication authentication) {
        return ResponseEntity.ok(converter.toResponse(pedidoAppUseCase.pronto(orderId, authentication.getName())));
    }

    @Operation(summary = "Entrega paga saiu da loja", description = "Exige o pedido já liquidado no caixa (Pix conferido).")
    @PostMapping("/app-orders/{orderId}/saiu")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<PedidoAppResponseDTO> saiu(@PathVariable Long orderId, Authentication authentication) {
        return ResponseEntity.ok(converter.toResponse(
                pedidoAppUseCase.saiuParaEntrega(orderId, authentication.getName())));
    }

    @Operation(summary = "Entrega concluída")
    @PostMapping("/app-orders/{orderId}/entregue")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<PedidoAppResponseDTO> entregue(@PathVariable Long orderId, Authentication authentication) {
        return ResponseEntity.ok(converter.toResponse(pedidoAppUseCase.entregue(orderId, authentication.getName())));
    }
}
