package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.request.ProductRequestCreateRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestEntryPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestEntryRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestPatchRequest;
import com.cernecommerce.adapter.in.dtos.request.ProductRequestStatusRequest;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestDashboardDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestDetailDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestEntryDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestEventDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestNotifyResultDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestSimilarDTO;
import com.cernecommerce.adapter.in.dtos.response.ProductRequestSummaryDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.estoque.ProductRequest;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntry;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEvent;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStats;
import com.cernecommerce.core.ports.in.ProductRequestUseCase;

import java.time.LocalDate;
import java.util.List;

/**
 * DTOs dos pedidos de clientes. {@code canSeePhone} vem do controller: sem {@code CRM_CUSTOMER_READ} o
 * telefone sai mascarado.
 */
public class ProductRequestDTOConverter {

    // ── Entrada ──────────────────────────────────────────────────────────────

    public ProductRequestUseCase.CreateCommand toCommand(ProductRequestCreateRequest r) {
        return new ProductRequestUseCase.CreateCommand(r.getDescription(), r.getBrand(), r.getVariation(),
                r.getCategoryId(), r.getCategoryOther(), toCommand(r.getEntry()));
    }

    public ProductRequestUseCase.EntryCommand toCommand(ProductRequestEntryRequest r) {
        return new ProductRequestUseCase.EntryCommand(r.getCustomerId(), r.getCustomerName(), r.getCustomerPhone(),
                r.getQuantity() == null ? 1 : r.getQuantity(), r.getMaxPrice(), r.getUrgency(), r.getNeededBy(),
                r.getNote());
    }

    public ProductRequestUseCase.PatchCommand toCommand(ProductRequestPatchRequest r) {
        boolean clearExpectedAt = r.getExpectedAt() != null && r.getExpectedAt().isEmpty();
        LocalDate expectedAt = r.getExpectedAt() == null || clearExpectedAt ? null : LocalDate.parse(r.getExpectedAt());
        return new ProductRequestUseCase.PatchCommand(r.getDescription(), r.getBrand(), r.getVariation(),
                r.getCategoryId(), r.getCategoryOther(), expectedAt, clearExpectedAt, r.getSupplierNote());
    }

    public ProductRequestUseCase.StatusCommand toCommand(ProductRequestStatusRequest r) {
        return new ProductRequestUseCase.StatusCommand(r.getStatus(), r.getExpectedAt(), r.getSupplierNote(),
                r.getReason(), Boolean.TRUE.equals(r.getWithoutSku()));
    }

    public ProductRequestUseCase.EntryPatchCommand toCommand(ProductRequestEntryPatchRequest r) {
        return new ProductRequestUseCase.EntryPatchCommand(r.getQuantity(), r.getMaxPrice(), r.getUrgency(),
                r.getNeededBy(), r.getNote());
    }

    // ── Saída ────────────────────────────────────────────────────────────────

    public PageResult<ProductRequestSummaryDTO> toResponse(PageResult<ProductRequestUseCase.View> page) {
        return new PageResult<>(toSummaries(page.content()), page.page(), page.size(), page.totalElements(),
                page.totalPages());
    }

    public List<ProductRequestSummaryDTO> toSummaries(List<ProductRequestUseCase.View> views) {
        return views.stream().map(this::toSummary).toList();
    }

    public ProductRequestSummaryDTO toSummary(ProductRequestUseCase.View v) {
        ProductRequest r = v.request();
        ProductRequestStats s = v.stats();
        return new ProductRequestSummaryDTO(r.id(), r.description(), r.brand(), r.variation(), r.categoryId(),
                v.categoryName(), r.status(), r.linkedSku(), v.linkedProductName(), r.expectedAt(), s.entriesCount(),
                s.totalQuantity(), s.maxUrgency(), s.earliestNeededBy(), s.maxPriceMin(), s.maxPriceMax(),
                s.pendingNotifications(), r.createdAt(), r.updatedAt(), r.version());
    }

    public ProductRequestDetailDTO toDetail(ProductRequestUseCase.Detail d, boolean canSeePhone) {
        ProductRequest r = d.view().request();
        ProductRequestStats s = d.view().stats();
        return new ProductRequestDetailDTO(r.id(), r.description(), r.brand(), r.variation(), r.categoryId(),
                d.view().categoryName(), r.status(), r.linkedSku(), d.view().linkedProductName(), r.expectedAt(),
                s.entriesCount(), s.totalQuantity(), s.maxUrgency(), s.earliestNeededBy(), s.maxPriceMin(),
                s.maxPriceMax(), s.pendingNotifications(), r.createdAt(), r.updatedAt(), r.version(),
                r.supplierNote(), r.categoryOther(), d.createdByName(), r.closedAt(),
                d.entries().stream().map(e -> toEntry(e, canSeePhone)).toList(),
                d.events().stream().map(this::toEvent).toList());
    }

    public ProductRequestEntryDTO toEntry(ProductRequestUseCase.EntryView v, boolean canSeePhone) {
        ProductRequestEntry e = v.entry();
        return new ProductRequestEntryDTO(e.id(), e.customerId(), v.customerName(),
                canSeePhone ? v.customerPhone() : maskPhone(v.customerPhone()), e.isRegistered(), e.quantity(),
                e.maxPrice(), e.urgency(), e.neededBy(), e.note(), e.status(), e.notifiedAt(), e.notifiedChannel(),
                v.createdByName(), e.createdAt());
    }

    public ProductRequestEventDTO toEvent(ProductRequestUseCase.EventView v) {
        ProductRequestEvent e = v.event();
        return new ProductRequestEventDTO(e.id(), e.type().name(), e.fromStatus(), e.toStatus(), e.entryId(),
                e.payload(), v.actorName(), e.at());
    }

    public List<ProductRequestSimilarDTO> toSimilar(List<ProductRequestUseCase.Similar> similar) {
        return similar.stream()
                .map(s -> new ProductRequestSimilarDTO(s.request().id(), s.request().description(),
                        s.request().brand(), s.request().variation(), s.request().status(), s.entriesCount(),
                        s.similarity()))
                .toList();
    }

    public ProductRequestDashboardDTO toDashboard(ProductRequestUseCase.Dashboard d) {
        return new ProductRequestDashboardDTO(d.pending(), d.ordered(), d.arrived(), d.awaitingNotification(),
                d.topRequested().stream()
                        .map(t -> new ProductRequestDashboardDTO.Top(t.id(), t.description(), t.entriesCount()))
                        .toList());
    }

    public ProductRequestNotifyResultDTO toNotifyResult(ProductRequestUseCase.NotifyResult r, boolean canSeePhone) {
        return new ProductRequestNotifyResultDTO(r.notified(), r.skippedNoConsent(), r.skippedNoAutomation(),
                toDetail(r.request(), canSeePhone));
    }

    /**
     * {@code "85999991234"} → {@code "(85) 9****-1234"}. O DDI 55, se houver, sai. Número curto demais para
     * mascarar com segurança vira só asteriscos.
     */
    static String maskPhone(String digits) {
        if (digits == null || digits.isBlank()) {
            return null;
        }
        String d = digits.replaceAll("\\D", "");
        if (d.length() >= 12 && d.startsWith("55")) {
            d = d.substring(2);
        }
        if (d.length() < 10) {
            return "****";
        }
        String ddd = d.substring(0, 2);
        String number = d.substring(2);
        String last4 = number.substring(number.length() - 4);
        return "(" + ddd + ") " + number.charAt(0) + "*".repeat(number.length() - 5) + "-" + last4;
    }
}
