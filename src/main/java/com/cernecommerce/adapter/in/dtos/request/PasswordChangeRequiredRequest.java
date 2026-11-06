package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.NotBlank;

/** A regra da senha nova fica no serviço, para responder INVALID_PASSWORD como o resto do login. */
public record PasswordChangeRequiredRequest(@NotBlank String passwordChangeToken, @NotBlank String newPassword) {
}
