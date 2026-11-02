package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.compras.NfeAlreadyReceivedException;
import com.cernecommerce.core.domain.exception.compras.NfeImportAlreadyProcessedException;
import com.cernecommerce.core.domain.exception.compras.NfeImportNotFoundException;
import com.cernecommerce.core.domain.exception.compras.SupplierNotFoundByTaxIdException;
import com.cernecommerce.core.domain.exception.compras.UnmatchedNfeLineException;
import com.cernecommerce.core.domain.model.compras.GoodsReceipt;
import com.cernecommerce.core.domain.model.compras.GoodsReceiptItem;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.domain.model.compras.NfeImportPreview;
import com.cernecommerce.core.domain.model.compras.NfeImportStatus;
import com.cernecommerce.core.domain.model.compras.Supplier;
import com.cernecommerce.core.domain.model.compras.SupplierProductMapping;
import com.cernecommerce.core.ports.in.ComprasUseCase;
import com.cernecommerce.core.ports.in.NfeImportUseCase;
import com.cernecommerce.core.ports.out.compras.NfeImportRepository;
import com.cernecommerce.core.ports.out.compras.SupplierProductMappingRepository;
import com.cernecommerce.core.ports.out.compras.SupplierRepository;
import com.cernecommerce.core.ports.out.estoque.NfeXmlImportPort;
import com.cernecommerce.core.ports.out.storage.NfeImportStoragePort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Importação de entrada de mercadoria por XML de NF-e (EST-F005). Ver o javadoc de
 * {@link NfeImportUseCase} para o porquê do fluxo em duas fases.
 */
public class NfeImportService implements NfeImportUseCase {

    private final NfeXmlImportPort nfeXmlImportPort;
    private final SupplierRepository supplierRepository;
    private final NfeImportStoragePort nfeImportStoragePort;
    private final NfeImportRepository nfeImportRepository;
    private final ComprasUseCase comprasUseCase;
    /** V182 — fator e SKU lembrados por fornecedor + cProd. Nulo nos testes antigos: sem memória. */
    private final SupplierProductMappingRepository mappingRepository;

    public NfeImportService(NfeXmlImportPort nfeXmlImportPort, SupplierRepository supplierRepository,
            NfeImportStoragePort nfeImportStoragePort, NfeImportRepository nfeImportRepository,
            ComprasUseCase comprasUseCase) {
        this(nfeXmlImportPort, supplierRepository, nfeImportStoragePort, nfeImportRepository, comprasUseCase, null);
    }

    public NfeImportService(NfeXmlImportPort nfeXmlImportPort, SupplierRepository supplierRepository,
            NfeImportStoragePort nfeImportStoragePort, NfeImportRepository nfeImportRepository,
            ComprasUseCase comprasUseCase, SupplierProductMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
        this.nfeXmlImportPort = nfeXmlImportPort;
        this.supplierRepository = supplierRepository;
        this.nfeImportStoragePort = nfeImportStoragePort;
        this.nfeImportRepository = nfeImportRepository;
        this.comprasUseCase = comprasUseCase;
    }

    @Override
    @Transactional
    public NfeImport previewImport(byte[] xmlBytes, String username) {
        // Parsing primeiro: XML malformado não gera nenhuma trilha — não há nada de auditável
        // ainda. A partir daqui o XML é reconhecível como NF-e, então vale persistir mesmo que o
        // fornecedor não seja encontrado (REJECTED é justamente esse registro).
        NfeImportPreview preview = nfeXmlImportPort.parse(xmlBytes);
        String fileReference = nfeImportStoragePort.save(xmlBytes, "xml");

        return supplierRepository.findByTaxId(preview.emitterCnpj())
                .map((Supplier supplier) -> nfeImportRepository.save(NfeImport.previewed(supplier.id(),
                        preview.emitterCnpj(), fileReference, applyRememberedMappings(supplier.id(), preview.lines()),
                        username, preview.accessKey())))
                .orElseGet(() -> {
                    // Persiste o REJECTED antes de lançar — a rejeição em si é o que fica
                    // auditado; sem isso, toda tentativa de import de fornecedor desconhecido
                    // desapareceria sem rastro.
                    nfeImportRepository.save(NfeImport.rejected(preview.emitterCnpj(), fileReference,
                            preview.lines(), username, preview.accessKey()));
                    throw new SupplierNotFoundByTaxIdException(preview.emitterCnpj());
                });
    }

    @Override
    @Transactional
    public GoodsReceipt confirmImport(NfeImportConfirmCommand command, String username) {
        NfeImport nfeImport = nfeImportRepository.findById(command.nfeImportId())
                .orElseThrow(() -> new NfeImportNotFoundException(command.nfeImportId()));
        if (nfeImport.status() != NfeImportStatus.PREVIEWED) {
            throw new NfeImportAlreadyProcessedException(nfeImport.id(), nfeImport.status());
        }
        // V182 — a mesma nota não entra duas vezes. Conferido antes de tudo; o índice único parcial
        // da V182 cobre a corrida de dois confirms simultâneos.
        nfeImportRepository.findConfirmedByAccessKey(nfeImport.accessKey()).ifPresent(previous -> {
            throw new NfeAlreadyReceivedException(previous.accessKey(), previous.goodsReceiptId(),
                    previous.confirmedAt());
        });

        Map<Integer, LineOverride> overridesByItem = new HashMap<>();
        for (LineOverride override : command.overrides()) {
            overridesByItem.put(override.itemNumber(), override);
        }

        List<NfeImportLine> resolvedLines = new ArrayList<>(nfeImport.lines().size());
        for (NfeImportLine line : nfeImport.lines()) {
            LineOverride override = overridesByItem.get(line.itemNumber());
            NfeImportLine resolved = line;
            if (override != null && override.sku() != null) {
                resolved = resolved.withMatchedSku(override.sku());
            }
            if (override != null && override.unitsPerPackage() != null) {
                resolved = resolved.withUnitsPerPackage(override.unitsPerPackage());
            }
            resolvedLines.add(resolved);
        }
        NfeImport withResolvedLines = nfeImport.withLines(resolvedLines);

        // Toda linha precisa ter SKU resolvido ANTES de tocar em receiveGoods — mesma disciplina
        // de "validar tudo antes de mexer em estoque" de PdvService/ComandaService.
        List<Integer> stillUnmatched = withResolvedLines.unmatchedLines().stream()
                .map(NfeImportLine::itemNumber).toList();
        if (!stillUnmatched.isEmpty()) {
            throw new UnmatchedNfeLineException(stillUnmatched);
        }

        // V182 — "2 CX" com fator 12 entra como 24 unidades, e o custo vai por unidade de estoque.
        List<GoodsReceiptItem> items = resolvedLines.stream()
                .map(line -> new GoodsReceiptItem(line.matchedSku(), line.stockQuantity(), line.lotCode(),
                        line.expiryDate(), line.stockUnitCost()))
                .toList();

        GoodsReceipt receipt = comprasUseCase.receiveGoods(withResolvedLines.supplierId(),
                command.warehouseCode(), items, username);

        nfeImportRepository.save(
                withResolvedLines.confirmed(command.warehouseCode(), receipt.id(), Instant.now()));
        rememberMappings(withResolvedLines.supplierId(), resolvedLines, overridesByItem, username);
        return receipt;
    }

    /**
     * V182 — o preview já chega com o que este fornecedor ensinou: o fator de cada cProd e, para a
     * linha que o EAN não casou, o SKU lembrado. O operador ainda pode trocar tudo no confirm.
     */
    private List<NfeImportLine> applyRememberedMappings(Long supplierId, List<NfeImportLine> lines) {
        if (mappingRepository == null) {
            return lines;
        }
        Map<String, SupplierProductMapping> mappings = mappingRepository.findBySupplierAndCodes(supplierId,
                lines.stream().map(NfeImportLine::supplierProductCode).distinct().toList());
        if (mappings.isEmpty()) {
            return lines;
        }
        List<NfeImportLine> out = new ArrayList<>(lines.size());
        for (NfeImportLine line : lines) {
            SupplierProductMapping mapping = mappings.get(line.supplierProductCode());
            NfeImportLine applied = line;
            if (mapping != null) {
                applied = applied.withUnitsPerPackage(mapping.unitsPerPackage());
                if (applied.matchStatus() == NfeImportLine.MatchStatus.UNMATCHED && mapping.sku() != null) {
                    applied = applied.withMatchedSku(mapping.sku());
                }
            }
            out.add(applied);
        }
        return out;
    }

    /** Grava o que o operador corrigiu nesta nota, para a próxima do mesmo fornecedor. */
    private void rememberMappings(Long supplierId, List<NfeImportLine> lines, Map<Integer, LineOverride> overrides,
            String username) {
        if (mappingRepository == null || overrides.isEmpty()) {
            return;
        }
        for (NfeImportLine line : lines) {
            LineOverride override = overrides.get(line.itemNumber());
            if (override == null) {
                continue;
            }
            mappingRepository.upsert(new SupplierProductMapping(supplierId, line.supplierProductCode(),
                    line.matchedSku(), line.effectiveUnitsPerPackage()), username);
        }
    }
}
