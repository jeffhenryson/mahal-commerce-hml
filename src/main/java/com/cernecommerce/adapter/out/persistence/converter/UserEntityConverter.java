package com.cernecommerce.adapter.out.persistence.converter;

import com.cernecommerce.adapter.out.persistence.entity.PermissionEntity;
import com.cernecommerce.adapter.out.persistence.entity.RoleEntity;
import com.cernecommerce.adapter.out.persistence.entity.UserEntity;
import com.cernecommerce.core.domain.model.rbac.Permission;
import com.cernecommerce.core.domain.model.rbac.Role;
import com.cernecommerce.core.domain.model.auth.User;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class UserEntityConverter {

    public User toDomain(UserEntity entity) {
        if (entity == null) return null;
        return User.fromPersisted(entity.getId(), entity.getUsername(), entity.getPassword(),
                entity.isEnabled(), entity.getEmail(), entity.isEmailVerified(),
                entity.getPendingEmail(), entity.getAvatarFilename(),
                entity.getCreatedAt(), toDomainRoles(entity.getRoles()),
                entity.getGoogleId(), entity.getAuthProvider(),
                entity.getUserType(), entity.getCustomerId())
                .withSecurityState(entity.getFullName(), entity.getPhone(), entity.isTwoFactorRequired(),
                        entity.isMustChangePassword(), entity.getLastLoginAt(), entity.getLastLoginIp(),
                        entity.getDeletedAt());
    }

    public UserEntity toEntityBase(User domain) {
        if (domain == null) return null;
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());
        entity.setUsername(domain.getUsername());
        entity.setPassword(domain.getPassword());
        entity.setEnabled(domain.isEnabled());
        entity.setEmail(domain.getEmail());
        entity.setEmailVerified(domain.isEmailVerified());
        entity.setPendingEmail(domain.getPendingEmail());
        entity.setAvatarFilename(domain.getAvatarFilename());
        entity.setGoogleId(domain.getGoogleId());
        entity.setAuthProvider(domain.getAuthProvider());
        entity.setUserType(domain.getUserType());
        entity.setCustomerId(domain.getCustomerId());
        // A entidade é recriada a cada save: campo que não passar por aqui é apagado no banco.
        entity.setFullName(domain.getFullName());
        entity.setPhone(domain.getPhone());
        entity.setTwoFactorRequired(domain.isTwoFactorRequired());
        entity.setMustChangePassword(domain.isMustChangePassword());
        entity.setLastLoginAt(domain.getLastLoginAt());
        entity.setLastLoginIp(domain.getLastLoginIp());
        return entity;
    }

    public Set<Role> toDomainRoles(Set<RoleEntity> roleEntities) {
        if (roleEntities == null) return new HashSet<>();
        return roleEntities.stream()
                .map(re -> Role.of(re.getId(), re.getName(), toDomainPermissions(re.getPermissions())))
                .collect(Collectors.toSet());
    }

    private Set<Permission> toDomainPermissions(Set<PermissionEntity> permEntities) {
        if (permEntities == null) return new HashSet<>();
        return permEntities.stream()
                .map(pe -> Permission.of(pe.getId(), pe.getName()))
                .collect(Collectors.toSet());
    }
}
