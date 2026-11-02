package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.Cart;
import com.cernecommerce.core.ports.in.EcommerceUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller stub do domínio <b>ecommerce</b>.
 *
 * <p>Esqueleto: demonstra o fluxo Controller → {@link EcommerceUseCase} → service.
 * Endpoints previstos (TODO): carrinho, cupons, promoções, checkout/pagamentos.
 * Endpoint atual requer {@code ECOMMERCE_READ}.</p>
 */
@RestController
@RequestMapping("/ecommerce")
@Tag(name = "E-commerce", description = "Carrinhos do marketplace (leitura administrativa)")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class EcommerceController {

    private final EcommerceUseCase ecommerceUseCase;

    public EcommerceController(EcommerceUseCase ecommerceUseCase) {
        this.ecommerceUseCase = ecommerceUseCase;
    }

    @Operation(summary = "Lista carrinhos com itens do marketplace, mais recente primeiro",
            description = "Somente leitura. Carrinho vazio não aparece. Sem preço: o preço é resolvido no checkout.")
    @GetMapping("/carts")
    @PreAuthorize("hasAuthority('ECOMMERCE_READ')")
    public ResponseEntity<PageResult<Cart>> listCarts(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ecommerceUseCase.listCarts(page, size));
    }
}
