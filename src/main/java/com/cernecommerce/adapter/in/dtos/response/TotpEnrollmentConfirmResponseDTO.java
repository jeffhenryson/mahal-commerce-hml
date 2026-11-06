package com.cernecommerce.adapter.in.dtos.response;

import java.util.List;

public record TotpEnrollmentConfirmResponseDTO(List<String> backupCodes, TokenPairResponseDTO tokens) {
}
