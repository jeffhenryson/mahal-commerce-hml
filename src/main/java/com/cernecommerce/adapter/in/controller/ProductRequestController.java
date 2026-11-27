package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ProductRequestDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestCreateRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestEntryPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestEntryRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestLinkSkuRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestNotifyRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestStatusRequest;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestDashboardDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestDetailDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestNotifyResultDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestSimilarDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestSummaryDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.estoque.ProductRequestValidationException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.ProductRequestSort;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;
import com.cernecommerce.core.ports.in.ProductRequestUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estoque › Pedidos de clientes: produtos que a loja não vende e que clientes pediram no balcão.
 *
 * <p>{@code ESTOQUE_PRODUCT_REQUEST_CREATE} (atendente) registra, junta e consulta parecidos e o histórico do
 * cliente; o resto exige {@code ESTOQUE_PRODUCT_REQUEST_MANAGE}. O telefone das solicitações sai mascarado
 * para quem não tem {@code CRM_CUSTOMER_READ}.</p>
 */
@RestController
@RequestMapping("/estoque/product-requests")
@Tag(name = "Estoque (Pedidos de clientes)", description = "Produtos que a loja não tem e clientes pediram")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class ProductRequestController {

    private static final String MANAGE = "hasAuthority('ESTOQUE_PRODUCT_REQUEST_MANAGE')";
    private static final String CREATE_OR_MANAGE =
            "hasAnyAuthority('ESTOQUE_PRODUCT_REQUEST_CREATE','ESTOQUE_PRODUCT_REQUEST_MANAGE')";
    private static final String PHONE_AUTHORITY = "CRM_CUSTOMER_READ";

    private final ProductRequestUseCase useCase;
    private final ProductRequestDTOConverter converter;
    private final ApplicationEventPublisher publisher;

    public ProductRequestController(ProductRequestUseCase useCase, ProductRequestDTOConverter converter,
            ApplicationEventPublisher publisher) {
        this.useCase = useCase;
        this.converter = converter;
        this.publisher = publisher;
    }

    // ── Leitura ──────────────────────────────────────────────────────────────

    @Operation(summary = "Página de pedidos de clientes com agregados",
            description = "`status` aceita lista (`PENDING,ORDERED`). `q` busca na descrição/marca/variação sem acento. "
                    + "`sort` = `createdAt|entriesCount|totalQuantity|maxUrgency`,`asc|desc`.")
    @GetMapping
    @PreAuthorize(MANAGE)
    public ResponseEntity<PageResult<ProductRequestSummaryDTO>> list(
            @RequestParam(required = false) List<ProductRequestStatus> status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] parts = sort.split(",");
        ProductRequestSort field = ProductRequestSort.fromApiName(parts[0].trim());
        if (field == null) {
            throw new ProductRequestValidationException(
                    "sort aceita createdAt, entriesCount, totalQuantity ou maxUrgency");
        }
        SortDirection direction = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? SortDirection.ASC : SortDirection.DESC;
        Set<ProductRequestStatus> statuses = status == null || status.isEmpty()
                ? EnumSet.allOf(ProductRequestStatus.class) : EnumSet.copyOf(status);
        return ResponseEntity.ok(converter.toResponse(
                useCase.list(statuses, q, categoryId, field, direction, page, size)));
    }

    @Operation(summary = "KPIs da aba Pedidos de clientes")
    @GetMapping("/summary")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDashboardDTO> summary() {
        return ResponseEntity.ok(converter.toDashboard(useCase.summary()));
    }

    @Operation(summary = "Até 5 pedidos abertos parecidos",
            description = "Para sugerir juntar ao registrar. Item já vinculado ao `sku` informado vem primeiro.")
    @GetMapping("/similar")
    @PreAuthorize(CREATE_OR_MANAGE)
    public ResponseEntity<List<ProductRequestSimilarDTO>> similar(
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String variation,
            @RequestParam(required = false) String sku) {
        return ResponseEntity.ok(converter.toSimilar(useCase.similar(description, brand, variation, sku)));
    }

    @Operation(summary = "Pedidos em que o cliente tem solicitação", description = "Perfil do cliente e PDV.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND", content = @Content)
    })
    @GetMapping("/by-customer/{customerId}")
    @PreAuthorize(CREATE_OR_MANAGE)
    public ResponseEntity<List<ProductRequestSummaryDTO>> byCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(converter.toSummaries(useCase.byCustomer(customerId)));
    }

    @Operation(summary = "Detalhe: item, solicitações e histórico")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "PRODUCT_REQUEST_NOT_FOUND", content = @Content)
    })
    @GetMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> get(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(converter.toDetail(useCase.get(id), canSeePhone(auth)));
    }

    // ── Registro (atendente) ─────────────────────────────────────────────────

    @Operation(summary = "Registra um pedido de cliente com a primeira solicitação")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criado"),
            @ApiResponse(responseCode = "400", description = "CUSTOMER_REQUIRED ou VALIDATION_ERROR", content = @Content),
            @ApiResponse(responseCode = "404", description = "CUSTOMER_NOT_FOUND ou CATEGORY_NOT_FOUND", content = @Content)
    })
    @PostMapping
    @PreAuthorize(CREATE_OR_MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> create(@Valid @RequestBody ProductRequestCreateRequest request,
            Authentication auth) {
        ProductRequestUseCase.Detail created = useCase.create(converter.toCommand(request), auth.getName());
        audit(EventType.PRODUCT_REQUEST_CREATED, auth, created.view().request().id(),
                Map.of("description", created.view().request().description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(converter.toDetail(created, canSeePhone(auth)));
    }

    @Operation(summary = "Junta uma solicitação a um pedido aberto",
            description = "O mesmo cliente (ou o mesmo telefone avulso) com solicitação ativa soma a quantidade.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Solicitação registrada"),
            @ApiResponse(responseCode = "400", description = "CUSTOMER_REQUIRED ou VALIDATION_ERROR", content = @Content),
            @ApiResponse(responseCode = "404", description = "PRODUCT_REQUEST_NOT_FOUND ou CUSTOMER_NOT_FOUND",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION (pedido fechado) ou "
                    + "PRODUCT_REQUEST_CONFLICT", content = @Content)
    })
    @PostMapping("/{id}/entries")
    @PreAuthorize(CREATE_OR_MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> addEntry(@PathVariable Long id,
            @Valid @RequestBody ProductRequestEntryRequest request, Authentication auth) {
        ProductRequestUseCase.Detail detail = useCase.addEntry(id, converter.toCommand(request), auth.getName());
        audit(EventType.PRODUCT_REQUEST_ENTRY_ADDED, auth, id, Map.of("quantity", request.getQuantity()));
        return ResponseEntity.status(HttpStatus.CREATED).body(converter.toDetail(detail, canSeePhone(auth)));
    }

    // ── Gestão ───────────────────────────────────────────────────────────────

    @Operation(summary = "Edita descrição, marca, variação, categoria, previsão e nota do fornecedor",
            description = "Ausente/null = não muda; \"\" limpa os textos opcionais e a previsão.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION ou PRODUCT_REQUEST_CONFLICT",
                    content = @Content)
    })
    @PatchMapping("/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> update(@PathVariable Long id,
            @Valid @RequestBody ProductRequestPatchRequest request, Authentication auth) {
        return ResponseEntity.ok(converter.toDetail(
                useCase.update(id, converter.toCommand(request), auth.getName()), canSeePhone(auth)));
    }

    @Operation(summary = "Muda o status",
            description = "PENDING→ORDERED|ARRIVED|CANCELLED, ORDERED→PENDING|ARRIVED|CANCELLED, ARRIVED→DONE|CANCELLED. "
                    + "ARRIVED exige SKU vinculado ou `withoutSku: true` e dispara PEDIDO_PRODUTO_CHEGOU para os "
                    + "clientes do CRM ainda não avisados. CANCELLED exige `reason`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "SKU_REQUIRED_FOR_ARRIVED ou VALIDATION_ERROR",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION ou PRODUCT_REQUEST_CONFLICT",
                    content = @Content)
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> changeStatus(@PathVariable Long id,
            @Valid @RequestBody ProductRequestStatusRequest request, Authentication auth) {
        ProductRequestUseCase.Detail detail = useCase.changeStatus(id, converter.toCommand(request), auth.getName());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("to", request.getStatus().name());
        if (request.getReason() != null) {
            details.put("reason", request.getReason());
        }
        audit(EventType.PRODUCT_REQUEST_STATUS_CHANGED, auth, id, details);
        return ResponseEntity.ok(converter.toDetail(detail, canSeePhone(auth)));
    }

    @Operation(summary = "Edita uma solicitação")
    @PatchMapping("/{id}/entries/{entryId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> updateEntry(@PathVariable Long id, @PathVariable Long entryId,
            @Valid @RequestBody ProductRequestEntryPatchRequest request, Authentication auth) {
        return ResponseEntity.ok(converter.toDetail(
                useCase.updateEntry(id, entryId, converter.toCommand(request), auth.getName()), canSeePhone(auth)));
    }

    @Operation(summary = "Cancela uma solicitação (não apaga)",
            description = "Se não sobrar solicitação ativa, o pedido não fecha sozinho.")
    @DeleteMapping("/{id}/entries/{entryId}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> cancelEntry(@PathVariable Long id, @PathVariable Long entryId,
            Authentication auth) {
        return ResponseEntity.ok(converter.toDetail(useCase.cancelEntry(id, entryId, auth.getName()),
                canSeePhone(auth)));
    }

    @Operation(summary = "Vincula o SKU do catálogo (produto ou variação); `sku: null` desvincula",
            description = "Não mexe em estoque.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU_NOT_FOUND ou PRODUCT_REQUEST_NOT_FOUND",
                    content = @Content),
            @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION (pedido fechado)",
                    content = @Content)
    })
    @PostMapping("/{id}/link-sku")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestDetailDTO> linkSku(@PathVariable Long id,
            @Valid @RequestBody ProductRequestLinkSkuRequest request, Authentication auth) {
        ProductRequestUseCase.Detail detail = useCase.linkSku(id, request.getSku(), auth.getName());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sku", detail.view().request().linkedSku() == null ? "" : detail.view().request().linkedSku());
        audit(EventType.PRODUCT_REQUEST_LINKED, auth, id, details);
        return ResponseEntity.ok(converter.toDetail(detail, canSeePhone(auth)));
    }

    @Operation(summary = "Avisa os clientes de que o produto chegou",
            description = "AUTOMATION dispara PEDIDO_PRODUTO_CHEGOU (só cliente do CRM com contato). MANUAL só "
                    + "registra o aviso feito pelo WhatsApp da loja. Sem `entryIds`: todas as ativas não avisadas.")
    @PostMapping("/{id}/notify")
    @PreAuthorize(MANAGE)
    public ResponseEntity<ProductRequestNotifyResultDTO> notifyCustomers(@PathVariable Long id,
            @Valid @RequestBody ProductRequestNotifyRequest request, Authentication auth) {
        ProductRequestUseCase.NotifyResult result = useCase.notify(id, request.getEntryIds(), request.getChannel(),
                auth.getName());
        if (result.notified() > 0) {
            audit(EventType.PRODUCT_REQUEST_NOTIFIED, auth, id,
                    Map.of("channel", request.getChannel().name(), "count", result.notified()));
        }
        return ResponseEntity.ok(converter.toNotifyResult(result, canSeePhone(auth)));
    }

    private void audit(EventType type, Authentication auth, Long id, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>(extra);
        details.put("productRequestId", id);
        publisher.publishEvent(AuditEvent.of(type, auth.getName(), details));
    }

    private static boolean canSeePhone(Authentication auth) {
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority a : auth.getAuthorities()) {
            if (PHONE_AUTHORITY.equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
