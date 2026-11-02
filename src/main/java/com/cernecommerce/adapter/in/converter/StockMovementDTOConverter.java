package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.OrphanSkuResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.PurchaseHistoryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.StockMovementResponseDTO;
import com.cernecommerce.core.domain.model.estoque.MeasurementUnit;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OrphanSku;
import com.cernecommerce.core.domain.model.estoque.StockMovement;

public class StockMovementDTOConverter {

    public MovementType toType(String type) {
        return MovementType.valueOf(type);
    }

    public StockMovementResponseDTO toResponse(StockMovement movement, String warehouseCode) {
        return toResponse(movement, warehouseCode, null);
    }

    public StockMovementResponseDTO toResponse(StockMovement movement, String warehouseCode, MeasurementUnit unit) {
        StockMovementResponseDTO dto = new StockMovementResponseDTO();
        dto.setId(movement.id());
        dto.setSku(movement.sku());
        dto.setWarehouseCode(warehouseCode);
        dto.setType(movement.type().name());
        dto.setQuantity(movement.quantity());
        dto.setReason(movement.reason());
        dto.setUsername(movement.username());
        dto.setCreatedAt(movement.createdAt());
        dto.setLotCode(movement.lotCode());
        dto.setUnitCost(movement.unitCost());
        dto.setUnit(unit == null ? null : unit.name());
        dto.setOriginType(movement.origin() == null ? null : movement.origin().type().name());
        dto.setOriginId(movement.origin() == null ? null : movement.origin().id());
        dto.setGoodsReceiptId(movement.goodsReceiptId());
        dto.setBalanceBefore(movement.balanceBefore());
        dto.setBalanceAfter(movement.balanceAfter());
        return dto;
    }

    /**
     * Histórico de compras (item 2) — {@code supplierId}/{@code supplierName} resolvidos pelo
     * controller a partir de {@code movement.goodsReceiptId()} (a camada estoque não depende de
     * compras).
     */
    public PurchaseHistoryResponseDTO toPurchaseHistoryResponse(StockMovement movement, Long supplierId,
            String supplierName) {
        PurchaseHistoryResponseDTO dto = new PurchaseHistoryResponseDTO();
        dto.setQuantity(movement.quantity());
        dto.setUnitCost(movement.unitCost());
        dto.setPurchasedAt(movement.createdAt());
        dto.setSupplierId(supplierId);
        dto.setSupplierName(supplierName);
        dto.setGoodsReceiptId(movement.goodsReceiptId());
        dto.setLotCode(movement.lotCode());
        return dto;
    }

    /**
     * Diferente de {@link #toResponse(StockMovement, String)}, aqui o {@code warehouseCode} vem
     * do próprio domínio: o diagnóstico de integridade varre todos os depósitos de uma vez, então
     * não há um código informado na consulta para reaproveitar.
     */
    public OrphanSkuResponseDTO toResponse(OrphanSku orphan) {
        OrphanSkuResponseDTO dto = new OrphanSkuResponseDTO();
        dto.setSku(orphan.sku());
        dto.setWarehouseCode(orphan.warehouseCode());
        dto.setQuantity(orphan.quantity());
        dto.setMovementCount(orphan.movementCount());
        dto.setHasReorderPoint(orphan.hasReorderPoint());
        dto.setLastMovementAt(orphan.lastMovementAt());
        return dto;
    }
}
