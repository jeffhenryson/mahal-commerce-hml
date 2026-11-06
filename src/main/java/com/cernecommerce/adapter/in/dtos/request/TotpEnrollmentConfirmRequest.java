package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record TotpEnrollmentConfirmRequest(@NotBlank String enrollmentToken, @NotBlank String code) {
}
