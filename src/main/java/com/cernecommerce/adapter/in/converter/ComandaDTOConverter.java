package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.core.domain.model.pdv.SessionLink;
import com.cernecommerce.adapter.in.dtos.response.ComandaItemAddonResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ComandaItemResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.ComandaResponseDTO;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;

import java.util.List;

public class ComandaDTOConverter {

    public ComandaResponseDTO toResponse(Comanda comanda) {
        ComandaResponseDTO dto = new ComandaResponseDTO();
        dto.setId(comanda.id());
        dto.setSessionId(comanda.sessionId());
        dto.setWarehouseCode(comanda.warehouseCode());
        dto.setTableOrCustomerLabel(comanda.tableOrCustomerLabel());
        dto.setCustomerId(comanda.customerId());
        dto.setStatus(comanda.status().name());
        dto.setItems(comanda.items().stream().map(this::toResponse).toList());
        dto.setRunningTotal(comanda.runningTotal());
        dto.setPromotionDiscountTotal(comanda.promotionDiscountTotal());
        dto.setOrderId(comanda.orderId());
        dto.setOpenedBy(comanda.openedBy());
        dto.setOpenedAt(comanda.openedAt());
        dto.setClosedAt(comanda.closedAt());
        return dto;
    }

    public List<ComandaResponseDTO> toResponse(List<Comanda> comandas) {
        return comandas.stream().map(this::toResponse).toList();
    }

    private ComandaItemResponseDTO toResponse(ComandaItem item) {
        ComandaItemResponseDTO dto = new ComandaItemResponseDTO();
        dto.setId(item.id());
        dto.setSku(item.sku());
        dto.setProductName(item.productName());
        dto.setQuantity(item.quantity());
        dto.setUnitPrice(item.unitPrice());
        dto.setCostPrice(item.costPrice());
        dto.setSubtotal(item.subtotal());
        dto.setAddedAt(item.addedAt());
        dto.setMode(item.mode());
        dto.setCourtesy(item.courtesy());
        dto.setLinkedItemId(item.linkedItemId());
        dto.setNotes(item.notes());
        dto.setSurchargeAmount(item.surchargeAmount());
        dto.setCashbackExcluded(item.benefitsExcluded());
        dto.setClosedInOrderId(item.closedInOrderId());
        dto.setPackageUses(item.packageUses());
        dto.setPackageSessionsPerUnit(item.packageSessionsPerUnit());
        dto.setEssenciaSku(item.essenceSku());
        dto.setEssenciaSku2(item.essenceSku2());
        dto.setEssenciaPendente(item.isEssencePending());
        dto.setKitBundleId(item.kitBundleId());
        dto.setKitTemplateId(item.kitTemplateId());
        dto.setKitDiscountAmount(item.kitDiscountAmount());
        dto.setPromotionId(item.promotionId());
        dto.setPromotionName(item.promotionName());
        dto.setPromotionDiscountAmount(item.promotionDiscount());
        dto.setCompraNaLoja(item.storePurchase());
        dto.setStoreDiscountAmount(item.storeDiscountAmount());
        dto.setModalidade(item.modality() == null ? null : item.modality().name());
        // PDV-F061 — o incluso é um pacote como os outros para a tela; o que o distingue é `incluso`.
        dto.setVinculoSessao(item.sessionLink() == null ? null
                : item.isIncludedPackage() ? SessionLink.PACOTE_ESSENCIA.name() : item.sessionLink().name());
        dto.setIncluso(item.isIncludedPackage());
        if (item.session() != null) {
            dto.setSessionStatus(item.session().status());
            dto.setStartedAt(item.session().startedAt());
            dto.setDeliveredAt(item.session().deliveredAt());
            dto.setCollectedAt(item.session().collectedAt());
            dto.setPagarNoFinal(item.session().payLater());
            dto.setWithdrawn(item.session().isWithdrawn());
            dto.setWithdrawnReason(item.session().withdrawnReason());
            dto.setWithdrawnBy(item.session().withdrawnBy());
        }
        dto.setTierId(item.sessionTierId());
        dto.setEssencia(item.sessionEssencia());
        if (item.setup() != null) {
            dto.setVasoGrande(item.setup().vasoGrande());
            dto.setCarvao(item.setup().charcoal());
            dto.setCarvaoCortesia(item.setup().charcoalCourtesy());
            dto.setAdicionais(item.setup().addons().stream()
                    .map(a -> new ComandaItemAddonResponseDTO(a.addonId(), a.nome(), a.preco()))
                    .toList());
        }
        return dto;
    }
}
