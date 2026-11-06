package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class UserResponseDTO {
    private Long id;
    private String username;
    private boolean enabled;
    private String email;
    private boolean emailVerified;
    private String avatarUrl;
    private Instant createdAt;
    private List<String> roles;
    private List<String> permissions;
    /** PLAT-F005 — o cadastro de cliente do CRM ligado à conta; nulo sem vínculo. */
    private Long customerId;
    private String customerName;
    private String fullName;
    private String phone;
    private boolean totpEnabled;
    private boolean twoFactorRequired;
    private Instant lastLoginAt;
    /** Só nas contas excluídas ({@code GET /users?deleted=true}). */
    private Instant deletedAt;
}
