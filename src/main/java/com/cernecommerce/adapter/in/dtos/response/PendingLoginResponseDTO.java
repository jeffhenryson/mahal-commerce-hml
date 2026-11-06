package com.cernecommerce.adapter.in.dtos.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/** O login ainda não terminou. O cliente decide a próxima tela pelo token que veio preenchido. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PendingLoginResponseDTO(String status, String enrollmentToken, String passwordChangeToken,
        long expiresInSeconds) {
}
