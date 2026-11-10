package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ProductDTOConverter;
import com.cernecommerce.adapter.in.dtos.response.KitItemPublicResponseDTO;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.ShopUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * "O que vem no kit" do kit pré-montado, sem custo, para o app (público) e para a consulta do
 * operador no PDV. Os itens continuam fora de busca, catálogo, outros kits e cupom — só aparecem
 * pendurados no próprio kit.
 */
@RestController
@Tag(name = "Kit pré-montado (conteúdo)", description = "Itens do kit pré-montado para o app e o PDV")
@Validated
public class KitItemCatalogController {

    private final EstoqueUseCase estoqueUseCase;
    private final ShopUseCase shopUseCase;
    private final ProductDTOConverter converter;

    public KitItemCatalogController(EstoqueUseCase estoqueUseCase, ShopUseCase shopUseCase,
            ProductDTOConverter converter) {
        this.estoqueUseCase = estoqueUseCase;
        this.shopUseCase = shopUseCase;
        this.converter = converter;
    }

    @Operation(summary = "Itens de um kit pré-montado do catálogo público (sem custo)",
            description = "Mesma visibilidade de GET /shop/catalog/{sku}: produto fora do app dá 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado, inativo, sem preço ou fora do app", content = @Content),
            @ApiResponse(responseCode = "409", description = "KIT_NOT_PRE_MONTADO", content = @Content)
    })
    @GetMapping("/shop/catalog/{sku}/kit-items")
    public ResponseEntity<List<KitItemPublicResponseDTO>> listPublicKitItems(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        // Reaproveita a regra de visibilidade do detalhe público: lança 404 se o item não está no app.
        shopUseCase.getCatalogItem(sku);
        return ResponseEntity.ok(estoqueUseCase.listKitItems(sku).stream()
                .map(converter::toKitItemPublicResponse).toList());
    }

    @Operation(summary = "Itens de um kit pré-montado para consulta do operador no PDV (sem custo)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "SKU não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "KIT_NOT_PRE_MONTADO", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem permissão", content = @Content)
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/pdv/products/{sku}/kit-items")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<KitItemPublicResponseDTO>> listPdvKitItems(
            @PathVariable @NotBlank @Size(min = 3, max = 50) String sku) {
        return ResponseEntity.ok(estoqueUseCase.listKitItems(sku).stream()
                .map(converter::toKitItemPublicResponse).toList());
    }
}
