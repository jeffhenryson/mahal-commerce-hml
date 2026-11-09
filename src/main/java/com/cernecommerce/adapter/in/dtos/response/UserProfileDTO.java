package com.cernecommerce.adapter.in.dtos.response;

import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class UserProfileDTO {
    private Long id;
    private String username;
    private boolean enabled;
    private String email;
    private boolean emailVerified;
    private String pendingEmail;
    private String avatarUrl;
    private Instant createdAt;
    private List<String> roles;
    private List<String> permissions;
    // Cliente vinculado (PLAT-F005) — é o que mostra a aba Compras no perfil. Nulos sem vínculo.
    private Long customerId;
    private String customerName;
}
