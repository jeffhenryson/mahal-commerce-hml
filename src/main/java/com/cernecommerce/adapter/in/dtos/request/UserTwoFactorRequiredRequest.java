package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotNull;

public record UserTwoFactorRequiredRequest(@NotNull Boolean required) {
}
