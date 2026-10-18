package com.cernecommerce.core.ports.out.user;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;

import java.util.Optional;
import java.util.Set;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByUsername(String username);

    PageResult<User> findAll(int page, int size);

    void deleteById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    /** PLAT-F005 — a conta (operador ou cliente da loja) ligada ao cliente do CRM, se houver. */
    Optional<User> findByCustomerId(Long customerId);

    PageResult<User> findFiltered(String search, Boolean enabled, String sortBy, String sortDir, int page, int size, Set<String> excludeRoles);

    long countAll();

    long countEnabled();

    long countDisabled();

    /** Usernames de todos os usuários que possuem a role informada — usado para invalidar cache de authorities. */
    Set<String> findUsernamesByRole(String roleName);

    /** Usernames de todos os usuários que possuem a permissão informada (via alguma de suas roles). */
    Set<String> findUsernamesByPermission(String permissionName);
}