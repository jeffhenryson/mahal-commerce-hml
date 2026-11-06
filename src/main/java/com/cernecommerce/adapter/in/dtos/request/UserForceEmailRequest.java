package com.cernecommerce.adapter.in.dtos.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserForceEmailRequest(@NotBlank @Email(message = "Email inválido") @Size(max = 254) String email) {
}
