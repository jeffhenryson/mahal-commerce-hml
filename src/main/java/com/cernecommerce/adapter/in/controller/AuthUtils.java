package com.cernecommerce.adapter.in.controller;

import org.springframework.security.core.Authentication;

final class AuthUtils {

    private AuthUtils() {}

    /**
     * Retorna true se o caller tem ROLE_DEV ou DEV_ELEVATED.
     * Null-safe: retorna false se {@code auth} for null (ex: contexto de teste sem segurança).
     */
    static boolean isDevAccess(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_DEV".equals(a.getAuthority())
                        || "DEV_ELEVATED".equals(a.getAuthority()));
    }

    /**
     * Retorna true se o caller tem {@code authority}. Para quando {@code @PreAuthorize} libera a rota
     * por mais de uma permissão e o comportamento muda conforme qual delas ele tem (PDV-F046, PED-F004).
     * Null-safe, como {@link #isDevAccess}.
     */
    static boolean hasAuthority(Authentication auth, String authority) {
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
