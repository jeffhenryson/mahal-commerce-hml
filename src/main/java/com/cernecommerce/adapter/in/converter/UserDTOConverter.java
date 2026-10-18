package com.cernecommerce.adapter.in.converter;

import com.cernecommerce.adapter.in.dtos.response.UserProfileDTO;
import com.cernecommerce.adapter.in.dtos.response.UserResponseDTO;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.domain.model.auth.User;

import java.util.List;
import java.util.stream.Collectors;

public class UserDTOConverter {

    private final String avatarBaseUrl;

    public UserDTOConverter(String avatarBaseUrl) {
        this.avatarBaseUrl = avatarBaseUrl;
    }

    public UserResponseDTO toResponse(User user) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEnabled(user.isEnabled());
        dto.setEmail(user.getEmail());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setAvatarUrl(buildAvatarUrl(user.getAvatarFilename()));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(roles(user));
        dto.setPermissions(permissions(user));
        dto.setCustomerId(user.getCustomerId());
        return dto;
    }

    /** PLAT-F005 — com o nome do cliente vinculado já resolvido (nulo sem vínculo). */
    public UserResponseDTO toResponse(User user, String customerName) {
        UserResponseDTO dto = toResponse(user);
        dto.setCustomerName(customerName);
        return dto;
    }

    public UserProfileDTO toProfile(User user) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEnabled(user.isEnabled());
        dto.setEmail(user.getEmail());
        dto.setEmailVerified(user.isEmailVerified());
        dto.setPendingEmail(user.getPendingEmail());
        dto.setAvatarUrl(buildAvatarUrl(user.getAvatarFilename()));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoles(roles(user));
        dto.setPermissions(permissions(user));
        return dto;
    }

    private String buildAvatarUrl(String filename) {
        if (filename == null) return null;
        return avatarBaseUrl + "/avatars/" + filename;
    }

    private List<String> roles(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .sorted()
                .collect(Collectors.toList());
    }

    private List<String> permissions(User user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
