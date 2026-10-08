package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ShopCatalogDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.ShopRegisterRequest;
import java.util.List;
import java.util.Map;

import com.cernecommerce.adapter.in.converter.CategoryDTOConverter;
import com.cernecommerce.adapter.in.dtos.response.CategoryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCatalogItemDetailResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopCatalogItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ShopRegisterResponseDTO;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.ports.in.ShopUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Superfície pública do marketplace (Fatia 8, plano-pdv-marketplace.md §2.9/§5.4). Sem
 * {@code @PreAuthorize} por natureza — ver exceção documentada em
 * {@code arch/SecurityArchitectureTest} e as regras próprias em {@code SecurityConfig}. Login do
 * cliente reaproveita {@code POST /auth/login} (autenticação única, §2.11.1) — não há endpoint de
 * login separado aqui.
 */
@RestController
@RequestMapping("/shop")
@Validated
public class ShopController {

    private final ShopUseCase shopUseCase;
    private final ApplicationEventPublisher publisher;
    private final ShopCatalogDTOConverter catalogConverter;
    private final CategoryDTOConverter categoryConverter;

    public ShopController(ShopUseCase shopUseCase, ApplicationEventPublisher publisher,
            ShopCatalogDTOConverter catalogConverter, CategoryDTOConverter categoryConverter) {
        this.shopUseCase = shopUseCase;
        this.publisher = publisher;
        this.catalogConverter = catalogConverter;
        this.categoryConverter = categoryConverter;
    }

    @Operation(summary = "Autocadastro do cliente do marketplace",
            description = "Cria o Customer (CRM) e a conta de acesso (ROLE_CUSTOMER) vinculados. "
                    + "Login depois em POST /auth/login com o mesmo email/senha.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cliente cadastrado"),
            @ApiResponse(responseCode = "409", description = "Email já cadastrado (como cliente ou operador)", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas tentativas", content = @Content)
    })
    @PostMapping("/register")
    public ResponseEntity<ShopRegisterResponseDTO> register(@Valid @RequestBody ShopRegisterRequest request) {
        ShopUseCase.CustomerRegistration registration = shopUseCase.registerCustomer(
                request.getNome(), request.getEmail(), request.getContato(), request.getPassword());

        ShopRegisterResponseDTO response = new ShopRegisterResponseDTO();
        response.setCustomerId(registration.customer().id());
        response.setNome(registration.customer().nome());
        response.setEmail(registration.customer().email());

        publisher.publishEvent(AuditEvent.of(EventType.CUSTOMER_MARKETPLACE_REGISTERED, registration.user().getUsername(),
                Map.of("customerId", registration.customer().id())));
        return ResponseEntity.status(201).body(response);
    }

    @Operation(summary = "Categorias ativas do catálogo, na ordem da vitrine",
            description = "Destacadas primeiro, depois por ordem de exibição, depois por nome. "
                    + "É a lista que o app usa para montar a primeira linha de navegação.")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "OK"))
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponseDTO>> listCategories() {
        return ResponseEntity.ok(shopUseCase.listCategories().stream()
                .map(categoryConverter::toResponse)
                .toList());
    }

    @Operation(summary = "Catálogo público paginado (ECM-F002)",
            description = "Só produto ativo e precificado, com preço efetivo e disponibilidade no "
                    + "depósito padrão do marketplace. `onSale=true` restringe à lista de promoções; "
                    + "`categoryId` restringe a uma categoria. A ordem é a da vitrine: produtos de "
                    + "categoria em destaque vêm primeiro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "429", description = "Muitas requisições", content = @Content),
            @ApiResponse(responseCode = "503", description = "Depósito padrão do marketplace não configurado", content = @Content)
    })
    @GetMapping("/catalog")
    public ResponseEntity<PageResult<ShopCatalogItemResponseDTO>> listCatalog(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Size(max = 100) String search) {
        PageResult<ShopUseCase.CatalogItem> result = shopUseCase.listCatalog(page, size, onSale, categoryId,
                search);
        PageResult<ShopCatalogItemResponseDTO> response = new PageResult<>(
                result.content().stream().map(catalogConverter::toResponse).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Detalhe público de um item do catálogo, com variações (ECM-F002)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado, inativo ou sem preço", content = @Content),
            @ApiResponse(responseCode = "429", description = "Muitas requisições", content = @Content),
            @ApiResponse(responseCode = "503", description = "Depósito padrão do marketplace não configurado", content = @Content)
    })
    @GetMapping("/catalog/{sku}")
    public ResponseEntity<ShopCatalogItemDetailResponseDTO> getCatalogItem(@PathVariable String sku) {
        return ResponseEntity.ok(catalogConverter.toResponse(shopUseCase.getCatalogItem(sku)));
    }
}
