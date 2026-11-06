package com.cernecommerce.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cernecommerce.adapter.out.persistence.entity.UserEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserJpaRepository extends JpaRepository<UserEntity, Long> {

    // Query nativa de propósito: o @SQLRestriction da entidade esconde as contas excluídas do JPQL.
    @Query(value = "select * from users where id = :id and deleted_at is not null", nativeQuery = true)
    java.util.Optional<UserEntity> findDeletedById(@Param("id") Long id);

    @Query(value = """
            select u.* from users u
            where u.deleted_at is not null and u.user_type = 'OPERATOR'
              and (:excludeDev = false or not exists (
                    select 1 from user_roles ur join roles r on r.id = ur.role_id
                    where ur.user_id = u.id and r.name = 'ROLE_DEV'))
            order by u.deleted_at desc, u.id desc
            """, countQuery = """
            select count(*) from users u
            where u.deleted_at is not null and u.user_type = 'OPERATOR'
              and (:excludeDev = false or not exists (
                    select 1 from user_roles ur join roles r on r.id = ur.role_id
                    where ur.user_id = u.id and r.name = 'ROLE_DEV'))
            """, nativeQuery = true)
    org.springframework.data.domain.Page<UserEntity> findDeleted(@Param("excludeDev") boolean excludeDev,
            org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query(value = "update users set deleted_at = null, enabled = false where id = :id and deleted_at is not null",
            nativeQuery = true)
    void restoreDeleted(@Param("id") Long id);

    @Modifying
    @Query("update UserEntity u set u.lastLoginAt = :at, u.lastLoginIp = :ip where u.username = :username")
    void recordLogin(@Param("username") String username,
            @Param("at") java.time.Instant at,
            @Param("ip") String ip);
    Optional<UserEntity> findByUsername(String username);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.username = :username")
    Optional<UserEntity> findByUsernameWithRoles(@Param("username") String username);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.id = :id")
    Optional<UserEntity> findByIdWithRoles(@Param("id") Long id);

    @Query("select u.id from UserEntity u order by u.id")
    Page<Long> findAllIds(Pageable pageable);

    @Query("select distinct u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.id in :ids order by u.id")
    List<UserEntity> findAllWithRolesByIdIn(@Param("ids") List<Long> ids);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.email = :email")
    Optional<UserEntity> findByEmailWithRoles(@Param("email") String email);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.googleId = :googleId")
    Optional<UserEntity> findByGoogleIdWithRoles(@Param("googleId") String googleId);

    @Query("select u from UserEntity u left join fetch u.roles r left join fetch r.permissions where u.customerId = :customerId")
    Optional<UserEntity> findByCustomerIdWithRoles(@Param("customerId") Long customerId);

    /**
     * Retorna apenas o ID do usuário pelo username.
     * Usado por adaptadores de persistência vizinhos que precisam de uma referência JPA
     * para FK sem carregar toda a entidade (ex: RefreshTokenRepositoryImpl).
     */
    @Query("select u.id from UserEntity u where u.username = :username")
    Optional<Long> findIdByUsername(@Param("username") String username);

    long countByEnabledTrue();

    long countByEnabledFalse();

    /** Soft delete: marca deleted_at sem remover o registro do banco. */
    @Modifying
    @Query("UPDATE UserEntity u SET u.deletedAt = :deletedAt WHERE u.id = :id")
    void softDeleteById(@Param("id") Long id, @Param("deletedAt") Instant deletedAt);

    @Query("select u.username from UserEntity u join u.roles r where r.name = :roleName")
    List<String> findUsernamesByRoleName(@Param("roleName") String roleName);

    @Query("select distinct u.username from UserEntity u join u.roles r join r.permissions p where p.name = :permissionName")
    List<String> findUsernamesByPermissionName(@Param("permissionName") String permissionName);

    @Query("select distinct u from UserEntity u join u.roles r where r.id = :roleId")
    List<UserEntity> findAllContainingRole(@Param("roleId") Long roleId);
}
