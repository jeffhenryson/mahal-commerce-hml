package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.NfeLineOverrideRequest;
import com.cernecommerce.adapter.in.dtos.response.NfeImportLineResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.NfeImportResponseDTO;
import com.cernecommerce.core.domain.model.compras.NfeImport;
import com.cernecommerce.core.domain.model.compras.NfeImportLine;
import com.cernecommerce.core.ports.in.NfeImportUseCase.LineOverride;

import java.util.List;

public class NfeImportDTOConverter {

    public List<LineOverride> toOverrides(List<NfeLineOverrideRequest> requests) {
        return requests.stream()
                .map(r -> new LineOverride(r.getItemNumber(), r.getSku(), r.getUnitsPerPackage()))
                .toList();
    }

    public NfeImportResponseDTO toResponse(NfeImport nfeImport) {
        NfeImportResponseDTO dto = new NfeImportResponseDTO();
        dto.setId(nfeImport.id());
        dto.setSupplierId(nfeImport.supplierId());
        dto.setEmitterCnpj(nfeImport.emitterCnpj());
        dto.setWarehouseCode(nfeImport.warehouseCode());
        dto.setStatus(nfeImport.status().name());
        dto.setGoodsReceiptId(nfeImport.goodsReceiptId());
        dto.setLines(nfeImport.lines().stream().map(this::toResponse).toList());
        dto.setUploadedBy(nfeImport.uploadedBy());
        dto.setUploadedAt(nfeImport.uploadedAt());
        dto.setConfirmedAt(nfeImport.confirmedAt());
        dto.setAccessKey(nfeImport.accessKey());
        return dto;
    }

    private NfeImportLineResponseDTO toResponse(NfeImportLine line) {
        NfeImportLineResponseDTO dto = new NfeImportLineResponseDTO();
        dto.setId(line.id());
        dto.setItemNumber(line.itemNumber());
        dto.setSupplierProductCode(line.supplierProductCode());
        dto.setEan(line.ean());
        dto.setDescription(line.description());
        dto.setQuantity(line.quantity());
        dto.setUnitPrice(line.unitPrice());
        dto.setLotCode(line.lotCode());
        dto.setExpiryDate(line.expiryDate());
        dto.setMatchStatus(line.matchStatus().name());
        dto.setMatchedSku(line.matchedSku());
        dto.setUnit(line.unit());
        dto.setTaxUnit(line.taxUnit());
        dto.setTaxQuantity(line.taxQuantity());
        dto.setUnitsPerPackage(line.unitsPerPackage());
        return dto;
    }
}
