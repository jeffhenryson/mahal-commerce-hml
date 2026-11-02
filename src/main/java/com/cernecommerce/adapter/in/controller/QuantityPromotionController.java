package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.QuantityPromotionDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ActiveRequest;
import com.cernecommerce.adapter.in.dtos.request.QuantityPromotionRequest;
import com.cernecommerce.adapter.in.dtos.response.QuantityPromotionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.QuantityPromotionStatsResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.estoque.PromotionChannel;
import com.cernecommerce.core.domain.model.estoque.QuantityPromotion;
import com.cernecommerce.core.ports.in.QuantityPromotionUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Promoção por quantidade ("leve 4 por R$ 10"): o cadastro (Estoque › Promoções) e a lista das vigentes
 * que o PDV carrega ao abrir. Quem aplica o desconto é o servidor, na venda, na comanda e no checkout.
 *
 * <p>Escrita com {@code ESTOQUE_PROMOTION_MANAGE} ou {@code ESTOQUE_KIT_TEMPLATE_MANAGE}: os papéis que
 * já cuidavam dos kits continuam podendo, mesmo que a permissão nova ainda não tenha sido concedida.</p>
 */
@RestController
@Tag(name = "Estoque (Promoção por quantidade)", description = "Regras \"leve N por R$ X\" aplicadas pelo servidor")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class QuantityPromotionController {

    private static final String BASE = "/estoque/promotions/quantity";
    private static final String MANAGE =
            "hasAnyAuthority('ESTOQUE_PROMOTION_MANAGE','ESTOQUE_KIT_TEMPLATE_MANAGE')";

    private final QuantityPromotionUseCase useCase;
    private final QuantityPromotionDTOConverter converter;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Autowired
    public QuantityPromotionController(QuantityPromotionUseCase useCase, QuantityPromotionDTOConverter converter,
            ApplicationEventPublisher publisher) {
        this(useCase, converter, publisher, Clock.systemUTC());
    }

    QuantityPromotionController(QuantityPromotionUseCase useCase, QuantityPromotionDTOConverter converter,
            ApplicationEventPublisher publisher, Clock clock) {
        this.useCase = useCase;
        this.converter = converter;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Operation(summary = "Lista todas as promoções por quantidade, com o status de agora")
    @GetMapping(BASE)
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<List<QuantityPromotionResponseDTO>> list() {
        return ResponseEntity.ok(converter.toResponse(useCase.list(), clock.instant()));
    }

    @Operation(summary = "Detalhe de uma promoção por quantidade")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Promoção não encontrada (PROMOTION_NOT_FOUND)", content = @Content)
    })
    @GetMapping(BASE + "/{id}")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<QuantityPromotionResponseDTO> get(@PathVariable Long id) {
        return ResponseEntity.ok(converter.toResponse(useCase.get(id), clock.instant()));
    }

    @Operation(summary = "Cria uma promoção por quantidade",
            description = "`bundlePrice` maior ou igual ao preço normal do grupo não é erro: a regra só não aplica. "
                    + "Um SKU pode estar em mais de uma regra; vale a de menor `id`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Criada"),
            @ApiResponse(responseCode = "400", description = "PROMOTION_INVALID_QUANTITY, PROMOTION_INVALID_PRICE, "
                    + "PROMOTION_INVALID_PERIOD ou VALIDATION_ERROR (skus/channels vazios)", content = @Content),
            @ApiResponse(responseCode = "404", description = "SKU inexistente (PROMOTION_SKU_NOT_FOUND)", content = @Content),
            @ApiResponse(responseCode = "409", description = "PROMOTION_SKU_INACTIVE ou DUPLICATE_PROMOTION_NAME", content = @Content)
    })
    @PostMapping(BASE)
    @PreAuthorize(MANAGE)
    public ResponseEntity<QuantityPromotionResponseDTO> create(@Valid @RequestBody QuantityPromotionRequest request,
            Authentication authentication) {
        QuantityPromotion created = useCase.create(converter.toDomain(request), authentication.getName());
        audit(EventType.QUANTITY_PROMOTION_CREATED, authentication, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(converter.toResponse(created, clock.instant()));
    }

    @Operation(summary = "Substitui a promoção inteira", description = "Vendas já feitas não mudam.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Mesmos códigos da criação", content = @Content),
            @ApiResponse(responseCode = "404", description = "PROMOTION_NOT_FOUND ou PROMOTION_SKU_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "PROMOTION_SKU_INACTIVE ou DUPLICATE_PROMOTION_NAME", content = @Content)
    })
    @PutMapping(BASE + "/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<QuantityPromotionResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody QuantityPromotionRequest request, Authentication authentication) {
        QuantityPromotion updated = useCase.update(id, converter.toDomain(request), authentication.getName());
        audit(EventType.QUANTITY_PROMOTION_UPDATED, authentication, updated);
        return ResponseEntity.ok(converter.toResponse(updated, clock.instant()));
    }

    @Operation(summary = "Ativa ou desativa a promoção")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "PROMOTION_NOT_FOUND", content = @Content)
    })
    @PatchMapping(BASE + "/{id}/active")
    @PreAuthorize(MANAGE)
    public ResponseEntity<QuantityPromotionResponseDTO> setActive(@PathVariable Long id,
            @Valid @RequestBody ActiveRequest request, Authentication authentication) {
        QuantityPromotion saved = useCase.setActive(id, request.getActive(), authentication.getName());
        audit(request.getActive() ? EventType.QUANTITY_PROMOTION_ACTIVATED : EventType.QUANTITY_PROMOTION_DEACTIVATED,
                authentication, saved);
        return ResponseEntity.ok(converter.toResponse(saved, clock.instant()));
    }

    @Operation(summary = "Exclui a promoção", description = "Pedidos já feitos guardam o desconto e o nome da regra.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Excluída"),
            @ApiResponse(responseCode = "404", description = "PROMOTION_NOT_FOUND", content = @Content)
    })
    @DeleteMapping(BASE + "/{id}")
    @PreAuthorize(MANAGE)
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        QuantityPromotion existing = useCase.get(id);
        useCase.delete(id);
        audit(EventType.QUANTITY_PROMOTION_DELETED, authentication, existing);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Resultado da promoção no período",
            description = "Pedidos pagos que levaram a regra: vendas, grupos, unidades e desconto total. "
                    + "Sem `from`/`to`, os últimos 30 dias.")
    @GetMapping(BASE + "/{id}/stats")
    @PreAuthorize("hasAuthority('ESTOQUE_PRODUCT_READ')")
    public ResponseEntity<QuantityPromotionStatsResponseDTO> stats(@PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        Instant fim = to == null ? clock.instant() : to;
        Instant inicio = from == null ? fim.minus(30, ChronoUnit.DAYS) : from;
        return ResponseEntity.ok(converter.toResponse(useCase.stats(id, inicio, fim)));
    }

    @Operation(summary = "Promoções por quantidade vigentes agora no canal",
            description = "O PDV carrega uma vez ao abrir para mostrar o selo e a prévia. Quem aplica é o servidor.")
    @GetMapping("/pdv/promotions/quantity")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<QuantityPromotionResponseDTO>> listVigentes(
            @RequestParam(defaultValue = "PDV") PromotionChannel channel) {
        Instant now = clock.instant();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePrivate())
                .body(converter.toResponse(useCase.listVigentes(channel, now), now));
    }

    private void audit(EventType type, Authentication authentication, QuantityPromotion p) {
        publisher.publishEvent(AuditEvent.of(type, authentication.getName(),
                Map.of("promotionId", String.valueOf(p.id()), "name", p.name(),
                        "quantity", String.valueOf(p.quantity()), "bundlePrice", p.bundlePrice().toPlainString())));
    }
}
