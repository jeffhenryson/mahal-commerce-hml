package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.CashMovementResponseDTO;
import com.cernecommerce.adapter.in.dtos.response.CashRegisterSessionResponseDTO;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashMovement;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;

import java.time.Instant;
import java.util.List;

public class CashRegisterDTOConverter {

    public CashRegisterSessionResponseDTO toResponse(CashRegisterSession session) {
        CashRegisterSessionResponseDTO dto = new CashRegisterSessionResponseDTO();
        dto.setId(session.id());
        dto.setOperator(session.operator());
        dto.setOpenedAt(session.openedAt());
        dto.setOpeningAmount(session.openingAmount());
        dto.setWarehouseCode(session.warehouseCode());
        dto.setClosedAt(session.closedAt());
        dto.setClosedBy(session.closedBy());
        dto.setExpectedAmount(session.expectedAmount());
        dto.setCountedAmount(session.countedAmount());
        dto.setDifferenceAmount(session.differenceAmount());
        dto.setDiverges(session.diverges());
        dto.setStatus(session.status().name());
        dto.setClosingNotes(session.closingNotes());
        Instant now = Instant.now();
        dto.setHorasAberto(session.openHours(now));
        dto.setFechamentoSugerido(session.closeSuggested(now));
        return dto;
    }

    public PageResult<CashRegisterSessionResponseDTO> toResponse(PageResult<CashRegisterSession> page) {
        return new PageResult<>(page.content().stream().map(this::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    public CashMovementResponseDTO toResponse(CashMovement movement) {
        CashMovementResponseDTO dto = new CashMovementResponseDTO();
        dto.setId(movement.id());
        dto.setSessionId(movement.sessionId());
        dto.setType(movement.type().name());
        dto.setAmount(movement.amount());
        dto.setSignedAmount(movement.signedAmount());
        dto.setReason(movement.reason());
        dto.setUsername(movement.username());
        dto.setCreatedAt(movement.createdAt());
        return dto;
    }

    public List<CashMovementResponseDTO> toMovementResponses(List<CashMovement> movements) {
        return movements.stream().map(this::toResponse).toList();
    }
}
