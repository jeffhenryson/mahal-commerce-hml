package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.dtos.request.AbrirEmbalagemRequest;
import com.cernecommerce.adapter.in.dtos.response.AbrirEmbalagemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CigarroFamilyResponseDTO;
import com.cernecommerce.core.domain.exception.pdv.CashSessionWarehouseMismatchException;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Central de cigarros do PDV (PDV-F041) — a tela de venda de solto e maço, no molde do assistente
 * Kit Mahal. A venda continua sendo {@code POST /pdv/sessions/{id}/sales} com o SKU do nível
 * escolhido, e abrir maço/carteira quando falta é da saída de estoque (EST-F032). PDV-F045 — o
 * atendente também abre à mão ({@code POST /pdv/cigarros/abrir}), com a permissão de venda e não a
 * de estoque, que ele não tem nem deve ter.
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
    private final PdvUseCase pdvUseCase;

    public PdvCigarrosController(EstoqueUseCase estoqueUseCase, PdvUseCase pdvUseCase) {
        this.estoqueUseCase = estoqueUseCase;
        this.pdvUseCase = pdvUseCase;
    }

    @Operation(summary = "Central de cigarros (PDV-F041)",
            description = "Todo produto ativo com embalagem ligada (EST-F032), com uma cadeia por cor — carteira, "
                    + "maço e solto —, e em cada nível o SKU vendável, o preço e o disponível no depósito. "
                    + "Para vender, use o `sku` do nível na venda de balcão: faltando solto, o sistema abre "
                    + "um maço sozinho; faltando maço, uma carteira. PDV-F045: mais todo produto ativo de "
                    + "categoria de cigarros (`cigarette`) sem embalagem, cada SKU numa cadeia de um nível.")
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

    @Operation(summary = "Abre uma embalagem no balcão (PDV-F045)",
            description = "Abre à mão `quantidade` embalagens fechadas do `sku` — 1 carteira em maços, 1 maço em "
                    + "soltos —, por exemplo para deixar maços avulsos na vitrine. Numa transação: SAIDA do SKU "
                    + "de fora **sem** abrir o nível de cima, ENTRADA de `quantidade × unitsPerParent` no de "
                    + "dentro com o custo médio de fora ÷ N, e STOCK_PACKAGE_BROKEN com o operador. Exige caixa "
                    + "aberto: o depósito é o dele.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aberta, com o disponível dos dois níveis"),
            @ApiResponse(responseCode = "400", description = "Corpo inválido, ou warehouseCode diferente do caixa (WAREHOUSE_MISMATCH)", content = @Content),
            @ApiResponse(responseCode = "403", description = "Sem PDV_SALE_MANAGE", content = @Content),
            @ApiResponse(responseCode = "404", description = "O SKU não contém nada (PACKAGING_NOT_FOUND), ou o operador sem caixa aberto (NO_OPEN_SESSION)", content = @Content),
            @ApiResponse(responseCode = "409", description = "Não há `quantidade` fechada disponível do SKU de fora (INSUFFICIENT_STOCK) — sem abrir o nível de cima", content = @Content)
    })
    @PostMapping("/pdv/cigarros/abrir")
    @PreAuthorize("hasAuthority('PDV_SALE_MANAGE')")
    public ResponseEntity<AbrirEmbalagemResponseDTO> abrirEmbalagem(@Valid @RequestBody AbrirEmbalagemRequest request,
            Authentication authentication) {
        CashRegisterSession caixa = pdvUseCase.getCurrentSession(authentication.getName());
        if (request.getWarehouseCode() != null && !request.getWarehouseCode().isBlank()
                && !request.getWarehouseCode().equalsIgnoreCase(caixa.warehouseCode())) {
            throw new CashSessionWarehouseMismatchException(request.getWarehouseCode(), caixa.warehouseCode());
        }
        EstoqueUseCase.OpenedPackaging aberta = estoqueUseCase.openPackagingManually(request.getSku().trim(),
                BigDecimal.valueOf(request.getQuantidade()), caixa.warehouseCode(), authentication.getName());
        return ResponseEntity.ok(new AbrirEmbalagemResponseDTO(
                new AbrirEmbalagemResponseDTO.Nivel(aberta.parentSku(), aberta.parentAvailable()),
                new AbrirEmbalagemResponseDTO.Nivel(aberta.childSku(), aberta.childAvailable())));
    }
}
