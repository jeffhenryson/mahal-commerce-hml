package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record TotpEnrollmentSetupRequest(@NotBlank String enrollmentToken) {
}
