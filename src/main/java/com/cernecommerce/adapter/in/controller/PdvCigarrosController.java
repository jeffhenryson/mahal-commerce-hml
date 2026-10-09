package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.response.CigarroFamilyResponseDTO;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Central de cigarros do PDV (PDV-F041) — a tela de venda de solto e maço, no molde do assistente
 * Kit Mahal. Só leitura: a venda continua sendo {@code POST /pdv/sessions/{id}/sales} com o SKU do
 * nível escolhido, e abrir maço/carteira quando falta é da saída de estoque (EST-F032).
 *
 * <p>Controller próprio, e não mais um método em {@code PdvController}: a fonte é o estoque (cadeia,
 * preço e saldo), e o PDV não precisa de um port novo para isso.</p>
 */
@Tag(name = "PDV (Vendas Balcão)", description = "Frente de caixa")
@SecurityRequirement(name = "bearerAuth")
@RestController
@Validated
public class PdvCigarrosController {

    private final EstoqueUseCase estoqueUseCase;

    public PdvCigarrosController(EstoqueUseCase estoqueUseCase) {
        this.estoqueUseCase = estoqueUseCase;
    }

    @Operation(summary = "Central de cigarros (PDV-F041)",
            description = "Todo produto ativo com embalagem ligada (EST-F032), com uma cadeia por cor — carteira, "
                    + "maço e solto —, e em cada nível o SKU vendável, o preço e o disponível no depósito. "
                    + "Para vender, use o `sku` do nível na venda de balcão: faltando solto, o sistema abre "
                    + "um maço sozinho; faltando maço, uma carteira.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "warehouseCode ausente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Depósito não encontrado", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem PDV_READ", content = @Content)
    })
    @GetMapping("/pdv/cigarros")
    @PreAuthorize("hasAuthority('PDV_READ')")
    public ResponseEntity<List<CigarroFamilyResponseDTO>> listCigarros(
            @RequestParam @NotBlank @Size(min = 2, max = 50) String warehouseCode) {
        return ResponseEntity.ok(estoqueUseCase.listPackagedFamilies(warehouseCode).stream()
                .map(f -> new CigarroFamilyResponseDTO(f.productSku(), f.productName(), f.lines().stream()
                        .map(l -> new CigarroFamilyResponseDTO.Line(l.levels().stream()
                                .map(v -> new CigarroFamilyResponseDTO.Level(v.sku(), v.label(), v.price(),
                                        v.available(), v.containsSku(), v.containsUnits()))
                                .toList()))
                        .toList()))
                .toList());
    }
}
