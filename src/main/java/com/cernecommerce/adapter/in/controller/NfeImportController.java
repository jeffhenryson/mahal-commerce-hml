package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.GoodsReceiptDTOConverter;
import com.cernecommerce.adapter.in.converter.NfeImportDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.NfeImportConfirmRequest;
import com.cernecommerce.adapter.in.dtos.response.GoodsReceiptResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.NfeImportResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.exception.compras.MalformedNfeXmlException;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.ports.in.NfeImportUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase.NfeImportConfirmCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Importação de entrada de mercadoria por XML de NF-e (EST-F005). Fluxo em duas fases — ver o
 * javadoc de {@link NfeImportUseCase}. Mesma permissão de registrar recebimento manual
 * ({@code COMPRAS_RECEIPT_MANAGE}): importar por XML é a mesma autoridade, só o dado de entrada
 * muda.
 */
@RestController
@RequestMapping("/compras/goods-receipts")
@Tag(name = "Compras (Importação de NF-e)", description = "Entrada de mercadoria por XML de NF-e")
@SecurityRequirement(name = "bearerAuth")
public class NfeImportController {

    private final NfeImportUseCase nfeImportUseCase;
    private final NfeImportDTOConverter nfeImportConverter;
    private final GoodsReceiptDTOConverter goodsReceiptConverter;
    private final ApplicationEventPublisher publisher;

    public NfeImportController(NfeImportUseCase nfeImportUseCase, NfeImportDTOConverter nfeImportConverter,
            GoodsReceiptDTOConverter goodsReceiptConverter, ApplicationEventPublisher publisher) {
        this.nfeImportUseCase = nfeImportUseCase;
        this.nfeImportConverter = nfeImportConverter;
        this.goodsReceiptConverter = goodsReceiptConverter;
        this.publisher = publisher;
    }

    @Operation(summary = "Parseia um XML de NF-e e casa fornecedor/itens, sem persistir recebimento",
            description = "Casa o fornecedor pelo CNPJ do emitente e cada item pelo EAN, quando bate "
                    + "com um produto do catálogo. Linhas sem EAN batido voltam marcadas UNMATCHED — "
                    + "confirme com POST .../nfe-confirm informando o SKU manual para elas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = NfeImportResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "XML malformado", content = @Content),
            @ApiResponse(responseCode = "404", description = "Nenhum fornecedor cadastrado com o CNPJ do emitente", content = @Content)
    })
    @PostMapping(value = "/nfe-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('COMPRAS_RECEIPT_MANAGE')")
    public ResponseEntity<NfeImportResponseDTO> previewImport(@RequestParam("file") MultipartFile file,
            Authentication authentication) {
        byte[] bytes;
        try {
            bytes = (file == null || file.isEmpty()) ? new byte[0] : file.getBytes();
        } catch (IOException e) {
            // EST-C021 — falha de leitura do upload, não do conteúdo; a mensagem do IOException é
            // do servidor de aplicação e não diz nada ao operador.
            throw MalformedNfeXmlException.fromParser(e);
        }
        if (bytes.length == 0) {
            throw new MalformedNfeXmlException("arquivo vazio");
        }
        NfeImport nfeImport = nfeImportUseCase.previewImport(bytes, authentication.getName());
        return ResponseEntity.ok(nfeImportConverter.toResponse(nfeImport));
    }

    @Operation(summary = "Confirma um import previamente aceito, gerando o recebimento de mercadoria",
            description = "Exige SKU resolvido em toda linha — por EAN no preview, ou por override "
                    + "manual aqui. Delega para o mesmo caminho de POST /compras/goods-receipts.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Confirmado", content = @Content(schema = @Schema(implementation = GoodsReceiptResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Linha sem SKU resolvido, faltando override", content = @Content),
            @ApiResponse(responseCode = "404", description = "Import ou depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Import já confirmado ou rejeitado (NFE_IMPORT_ALREADY_PROCESSED), "
                    + "ou outra importação com a mesma chave de acesso já recebida (NFE_ALREADY_RECEIVED, com "
                    + "goodsReceiptId e receivedAt)", content = @Content(schema = @Schema(implementation = com.cernecommerce.infra.handler.NfeAlreadyReceivedError.class)))
    })
    @PostMapping("/nfe-confirm")
    @PreAuthorize("hasAuthority('COMPRAS_RECEIPT_MANAGE')")
    public ResponseEntity<GoodsReceiptResponseDTO> confirmImport(@Valid @RequestBody NfeImportConfirmRequest request,
            Authentication authentication) {
        NfeImportConfirmCommand command = new NfeImportConfirmCommand(request.getNfeImportId(),
                request.getWarehouseCode(), nfeImportConverter.toOverrides(request.getOverrides()));
        GoodsReceipt receipt = nfeImportUseCase.confirmImport(command, authentication.getName());
        publisher.publishEvent(AuditEvent.of(EventType.STOCK_MOVEMENT_REGISTERED, authentication.getName(),
                Map.of("origin", "NFE_IMPORT",
                        "nfeImportId", request.getNfeImportId(),
                        "supplierId", receipt.supplierId(),
                        "warehouseCode", receipt.warehouseCode(),
                        "type", MovementType.ENTRADA.name(),
                        "itemCount", receipt.items().size())));
        return ResponseEntity.status(201).body(goodsReceiptConverter.toResponse(receipt));
    }
}
