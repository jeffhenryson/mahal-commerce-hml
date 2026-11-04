package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.CashbackSettingsRequest;
import com.cernecommerce.adapter.in.dtos.response.CashbackBalanceResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackRedemptionQuoteResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackSettingsResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackEntryResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackMarginImpactResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashbackRateResponseDTO;
import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.cashback.CashbackEntry;
import com.cernecommerce.core.domain.model.cashback.CashbackMarginImpactItem;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackRedemptionQuote;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;
import com.cernecommerce.core.domain.model.cashback.CashbackSettings;

public class CashbackDTOConverter {

    public CashbackScope toScope(String scope) {
        return CashbackScope.valueOf(scope);
    }

    public CashbackRateResponseDTO toResponse(CashbackRate rate) {
        CashbackRateResponseDTO dto = new CashbackRateResponseDTO();
        dto.setId(rate.id());
        dto.setScope(rate.scope().name());
        dto.setScopeRef(rate.scopeRef());
        dto.setPercent(rate.percent());
        dto.setActive(rate.active());
        dto.setValidFrom(rate.validFrom());
        dto.setValidTo(rate.validTo());
        dto.setCreatedAt(rate.createdAt());
        return dto;
    }

    public CashbackEntryResponseDTO toResponse(CashbackEntry entry) {
        CashbackEntryResponseDTO dto = new CashbackEntryResponseDTO();
        dto.setId(entry.id());
        dto.setCustomerId(entry.customerId());
        dto.setOrderId(entry.orderId());
        dto.setOrderItemId(entry.orderItemId());
        dto.setType(entry.type().name());
        dto.setAmount(entry.amount());
        dto.setAvailableAt(entry.availableAt());
        dto.setExpiresAt(entry.expiresAt());
        dto.setReversesEntryId(entry.reversesEntryId());
        dto.setCreatedAt(entry.createdAt());
        return dto;
    }

    public CashbackBalanceResponseDTO toResponse(CashbackBalance balance) {
        CashbackBalanceResponseDTO dto = new CashbackBalanceResponseDTO();
        dto.setAvailable(balance.available());
        dto.setPending(balance.pending());
        dto.setExpiringSoon(balance.expiringSoon());
        return dto;
    }

    public CashbackMarginImpactResponseDTO toResponse(CashbackMarginImpactItem item) {
        CashbackMarginImpactResponseDTO dto = new CashbackMarginImpactResponseDTO();
        dto.setSku(item.sku());
        dto.setName(item.name());
        dto.setMarginPercent(item.marginPercent());
        dto.setCashbackPercent(item.cashbackPercent());
        dto.setMarginShareConsumed(item.marginShareConsumed());
        return dto;
    }

    public CashbackSettingsResponseDTO toResponse(CashbackSettings settings) {
        CashbackSettingsResponseDTO dto = new CashbackSettingsResponseDTO();
        dto.setCarenciaDias(settings.carenciaDias());
        dto.setExpiracaoDias(settings.expiracaoDias());
        dto.setResgateTetoPercent(settings.resgateTetoPercent());
        dto.setResgateMinimoSaldo(settings.resgateMinimoSaldo());
        return dto;
    }

    public CashbackSettings toSettings(CashbackSettingsRequest request) {
        return new CashbackSettings(request.getCarenciaDias(), request.getExpiracaoDias(),
                request.getResgateTetoPercent(), request.getResgateMinimoSaldo());
    }

    public CashbackRedemptionQuoteResponseDTO toResponse(CashbackRedemptionQuote quote) {
        CashbackRedemptionQuoteResponseDTO dto = new CashbackRedemptionQuoteResponseDTO();
        dto.setAvailable(quote.available());
        dto.setMinimumBalance(quote.minimumBalance());
        dto.setCapPercent(quote.capPercent());
        dto.setCapAmount(quote.capAmount());
        dto.setMaxRedeemable(quote.maxRedeemable());
        dto.setReason(quote.reason());
        return dto;
    }
}
