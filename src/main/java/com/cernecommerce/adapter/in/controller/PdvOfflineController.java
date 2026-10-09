package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.OrderDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.DiscardOfflineSaleRequest;
import com.cernecommerce.adapter.in.dtos.request.OfflineSaleRequest;
import com.cernecommerce.adapter.in.dtos.request.OfflineSyncRequest;
import com.cernecommerce.adapter.in.dtos.response.OfflineRejectionResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.OfflineSyncResultDTO;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase;
import com.cernecommerce.core.ports.in.OfflineSaleUseCase.OfflineSaleCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Venda offline no balcão (PDV-F043): a fila do caixa sincronizada quando a rede volta, e a revisão
 * das vendas que não entraram. A fila em si (cache do catálogo, IndexedDB) é do frontend.
 */
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa")
@SecurityRequirement(name = "bearerAuth")
@RestController
@Validated
public class PdvOfflineController {

    private static final String DISCOUNT_AUTHORITY = "PDV_SALE_DISCOUNT";

    private final OfflineSaleUseCase offlineSaleUseCase;
    private final OrderDTOConverter orderConverter;

    public PdvOfflineController(OfflineSaleUseCase offlineSaleUseCase, OrderDTOConverter orderConverter) {
        this.offlineSaleUseCase = offlineSaleUseCase;
        this.orderConverter = orderConverter;
    }

    @Operation(summary = "Sincroniza as vendas feitas offline no caixa (PDV-F043)",
            description = "Até 50 vendas por chamada, cada uma na sua transação: uma recusada não derruba as "
                    + "outras. Por venda: `SYNCED` (registrada), `DUPLICATE` (o `clientSaleId` já estava "
                    + "registrado — reenviar a fila é seguro) ou `REJECTED` (falta de estoque, SKU que sumiu, "
                    + "sem preço…): a venda fica guardada para revisão e **o caixa não fecha** até ela ser "
                    + "reenviada ou descartada. O caixa precisa estar aberto e ser do operador. O lote inteiro "
                    + "é recusado com PIX/marcado (`400 OFFLINE_PAYMENT_NOT_ALLOWED`), com `soldAt` fora do "
                    + "caixa (`400 OFFLINE_SOLD_AT_OUT_OF_WINDOW`) e com desconto sem PDV_SALE_DISCOUNT (403).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado por venda, na ordem do lote"),
            @ApiResponse(responseCode = "400", description = "Lote inválido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Caixa de outro operador ou desconto sem permissão", content = @Content),
            @ApiResponse(responseCode = "409", description = "Caixa fechado (CASH_REGISTER_SESSION_CLOSED)", content = @Content)
    })
    @PostMapping("/pdv/sessions/{id}/sales/sync")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<List<OfflineSyncResultDTO>> sync(@PathVariable("id") Long sessionId,
            @Valid @RequestBody OfflineSyncRequest request, Authentication authentication) {
        List<OfflineSaleCommand> sales = request.getSales().stream().map(this::toCommand).toList();
        requireDiscountAuthority(sales, authentication);
        return ResponseEntity.ok(offlineSaleUseCase.sync(sessionId, sales, authentication.getName()).stream()
                .map(r -> new OfflineSyncResultDTO(r.clientSaleId(), r.status().name(), r.orderId(), r.rejectionId(),
                        r.errorCode(), r.message()))
                .toList());
    }

    @Operation(summary = "Vendas offline recusadas do caixa (PDV-F043)",
            description = "Pendentes e resolvidas, mais recentes primeiro. Pendente é o que barra o fechamento.")
    @GetMapping("/pdv/sessions/{id}/offline-rejections")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<OfflineRejectionResponseDTO>> listRejections(@PathVariable("id") Long sessionId) {
        return ResponseEntity.ok(offlineSaleUseCase.listRejections(sessionId).stream().map(this::toResponse).toList());
    }

    @Operation(summary = "Reenvia uma venda offline recusada (PDV-F043)",
            description = "Depois de acertar o que a recusou (ex.: dar entrada no estoque). Registra em nome do "
                    + "operador do caixa; quem reenviou fica em `resolvedBy`. Deu certo: `RETRIED` com o pedido. "
                    + "Falhou de novo: continua pendente, com o motivo novo em `errorCode`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A recusa, resolvida ou ainda pendente"),
            @ApiResponse(responseCode = "404", description = "OFFLINE_REJECTION_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já resolvida (OFFLINE_REJECTION_ALREADY_RESOLVED)", content = @Content)
    })
    @PostMapping("/pdv/offline-rejections/{id}/retry")
    @PreAuthorize("hasAuthority('PDV_OFFLINE_REVIEW')")
    public ResponseEntity<OfflineRejectionResponseDTO> retry(@PathVariable("id") Long rejectionId,
            Authentication authentication) {
        return ResponseEntity.ok(toResponse(offlineSaleUseCase.retry(rejectionId, authentication.getName())));
    }

    @Operation(summary = "Descarta uma venda offline recusada (PDV-F043)",
            description = "A venda não vai entrar no sistema — o dinheiro dela é acertado fora, e o motivo fica "
                    + "registrado. Libera o fechamento do caixa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Descartada"),
            @ApiResponse(responseCode = "400", description = "Motivo ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "OFFLINE_REJECTION_NOT_FOUND", content = @Content),
            @ApiResponse(responseCode = "409", description = "Já resolvida (OFFLINE_REJECTION_ALREADY_RESOLVED)", content = @Content)
    })
    @PostMapping("/pdv/offline-rejections/{id}/discard")
    @PreAuthorize("hasAuthority('PDV_OFFLINE_REVIEW')")
    public ResponseEntity<OfflineRejectionResponseDTO> discard(@PathVariable("id") Long rejectionId,
            @Valid @RequestBody DiscardOfflineSaleRequest request, Authentication authentication) {
        return ResponseEntity.ok(toResponse(offlineSaleUseCase.discard(rejectionId, request.getReason(),
                authentication.getName())));
    }

    private OfflineSaleCommand toCommand(OfflineSaleRequest r) {
        List<SaleItemCommand> items = orderConverter.toCommands(r.getItems());
        List<PaymentCommand> payments = orderConverter.toPaymentCommands(r.getPayments());
        return new OfflineSaleCommand(r.getClientSaleId(), r.getSoldAt(), r.getCustomerId(),
                items.stream().map(i -> new OfflineSaleItem(i.sku(), i.quantity(), i.discountAmount(), i.note())).toList(),
                payments.stream().map(p -> new OfflineSalePayment(p.method(), p.amount(), p.installments())).toList());
    }

    /** Mesma regra da venda online: desconto exige PDV_SALE_DISCOUNT. Checada no lote inteiro, antes de tudo. */
    private static void requireDiscountAuthority(List<OfflineSaleCommand> sales, Authentication authentication) {
        boolean hasDiscount = sales.stream().flatMap(s -> s.items().stream())
                .anyMatch(i -> i.discountAmount() != null && i.discountAmount().compareTo(BigDecimal.ZERO) > 0);
        if (!hasDiscount) {
            return;
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(DISCOUNT_AUTHORITY::equals);
        if (!allowed) {
            throw new AccessDeniedException("Conceder desconto exige a permissão " + DISCOUNT_AUTHORITY + ".");
        }
    }

    private OfflineRejectionResponseDTO toResponse(OfflineSaleRejection r) {
        OfflineRejectionResponseDTO dto = new OfflineRejectionResponseDTO();
        dto.setId(r.id());
        dto.setSessionId(r.sessionId());
        dto.setClientSaleId(r.clientSaleId());
        dto.setClientSoldAt(r.clientSoldAt());
        dto.setCustomerId(r.customerId());
        dto.setItems(r.items().stream().map(i -> {
            OfflineRejectionResponseDTO.Item item = new OfflineRejectionResponseDTO.Item();
            item.setSku(i.sku());
            item.setQuantity(i.quantity());
            item.setDiscountAmount(i.discountAmount());
            item.setNote(i.note());
            return item;
        }).toList());
        dto.setPayments(r.payments().stream().map(p -> {
            OfflineRejectionResponseDTO.Payment payment = new OfflineRejectionResponseDTO.Payment();
            payment.setMethod(p.method().name());
            payment.setAmount(p.amount());
            payment.setInstallments(p.installments());
            return payment;
        }).toList());
        dto.setErrorCode(r.errorCode());
        dto.setMessage(r.message());
        dto.setCreatedAt(r.createdAt());
        dto.setCreatedBy(r.createdBy());
        dto.setResolution(r.resolution() == null ? null : r.resolution().name());
        dto.setResolvedAt(r.resolvedAt());
        dto.setResolvedBy(r.resolvedBy());
        dto.setResolutionNote(r.resolutionNote());
        dto.setOrderId(r.orderId());
        return dto;
    }
}
