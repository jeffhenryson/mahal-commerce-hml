package com.cernecommerce.adapter.out.persistence.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Subquery;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.cernecommerce.adapter.out.persistence.converter.UserEntityConverter;
import com.cernecommerce.adapter.out.persistence.entity.RoleEntity;
import com.cernecommerce.adapter.out.persistence.entity.UserEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.domain.model.auth.UserType;
import com.cernecommerce.core.ports.out.user.UserRepository;

@Repository
@Transactional
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userRepo;
    private final RoleJpaRepository roleJpaRepo;
    private final UserEntityConverter converter;

    @PersistenceContext
    private EntityManager entityManager;

    public UserRepositoryImpl(UserJpaRepository userRepo,
                              RoleJpaRepository roleJpaRepo,
                              UserEntityConverter converter) {
        this.userRepo = userRepo;
        this.roleJpaRepo = roleJpaRepo;
        this.converter = converter;
    }

    @Override
    public User save(User user) {
        UserEntity entity = converter.toEntityBase(user);

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            Set<RoleEntity> roleEntities = user.getRoles().stream()
                    .filter(r -> r.getId() != null)
                    .map(r -> roleJpaRepo.getReferenceById(r.getId()))
                    .collect(Collectors.toSet());
            entity.setRoles(roleEntities);
        }

        UserEntity saved = userRepo.save(entity);
        return converter.toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepo.findByIdWithRoles(id).map(converter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepo.findByUsernameWithRoles(username).map(converter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> findAll(int page, int size) {
        Page<Long> idPage = userRepo.findAllIds(PageRequest.of(page, size));
        List<User> users = idPage.isEmpty()
                ? List.of()
                : userRepo.findAllWithRolesByIdIn(idPage.getContent())
                          .stream()
                          .map(converter::toDomain)
                          .collect(Collectors.toList());
        return new PageResult<>(users, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    @Override
    public void deleteById(Long id) {
        userRepo.softDeleteById(id, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepo.findByEmailWithRoles(email).map(converter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByGoogleId(String googleId) {
        return userRepo.findByGoogleIdWithRoles(googleId).map(converter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<User> findFiltered(String search, Boolean enabled, String sortBy, String sortDir, int page, int size, Set<String> excludeRoles) {
        Sort sort = buildSort(sortBy, sortDir);
        Specification<UserEntity> spec = buildSpec(search, enabled, excludeRoles);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Long> idPage = findFilteredIds(spec, pageable);
        if (idPage.isEmpty()) return new PageResult<>(List.of(), page, size, 0L, 0);

        List<Long> orderedIds = idPage.getContent();
        Map<Long, UserEntity> byId = userRepo.findAllWithRolesByIdIn(orderedIds)
                .stream().collect(Collectors.toMap(UserEntity::getId, u -> u));
        List<User> users = orderedIds.stream()
                .filter(byId::containsKey)
                .map(id -> converter.toDomain(byId.get(id)))
                .collect(Collectors.toList());
        return new PageResult<>(users, page, size, idPage.getTotalElements(), idPage.getTotalPages());
    }

    // Seleciona apenas a coluna id, evitando trazer todas as colunas da tabela users
    // apenas para extrair o id depois. Equivalente ao antigo JPQL findFilteredIds mas
    // sem o pattern "? is null" que causa erro de tipo no PostgreSQL com parâmetros null.
    private Page<Long> findFilteredIds(Specification<UserEntity> spec, Pageable pageable) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<UserEntity> countRoot = countQuery.from(UserEntity.class);
        countQuery.select(cb.count(countRoot));
        Predicate countPred = spec.toPredicate(countRoot, countQuery, cb);
        if (countPred != null) countQuery.where(countPred);
        long total = entityManager.createQuery(countQuery).getSingleResult();

        if (total == 0) return new PageImpl<>(List.of(), pageable, 0);

        CriteriaQuery<Long> idQuery = cb.createQuery(Long.class);
        Root<UserEntity> root = idQuery.from(UserEntity.class);
        idQuery.select(root.get("id"));
        Predicate pred = spec.toPredicate(root, idQuery, cb);
        if (pred != null) idQuery.where(pred);

        List<jakarta.persistence.criteria.Order> orders = pageable.getSort().stream()
                .map(o -> o.isAscending()
                        ? cb.asc(root.get(o.getProperty()))
                        : cb.desc(root.get(o.getProperty())))
                .toList();
        if (!orders.isEmpty()) idQuery.orderBy(orders);

        TypedQuery<Long> query = entityManager.createQuery(idQuery);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        return new PageImpl<>(query.getResultList(), pageable, total);
    }

    private static Specification<UserEntity> buildSpec(String search, Boolean enabled, Set<String> excludeRoles) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // PLAT-C054: esta listagem alimenta a gestão de usuários do back-office. Contas de
            // cliente da loja (/shop/register) não são operadores e não podem aparecer nem ser
            // administradas por ela.
            predicates.add(cb.equal(root.get("userType"), UserType.OPERATOR));
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
                ));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (excludeRoles != null && !excludeRoles.isEmpty()) {
                Subquery<Long> sub = query.subquery(Long.class);
                var subUser = sub.from(UserEntity.class);
                var rolesJoin = subUser.join("roles");
                sub.select(subUser.get("id"))
                   .where(cb.and(
                       cb.equal(subUser.get("id"), root.get("id")),
                       rolesJoin.get("name").in(excludeRoles)
                   ));
                predicates.add(cb.not(cb.exists(sub)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Sort buildSort(String sortBy, String sortDir) {
        Set<String> allowed = Set.of("id", "username", "email", "enabled", "createdAt");
        String field = (sortBy != null && allowed.contains(sortBy)) ? sortBy : "id";
        Sort.Direction dir = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAll() {
        return userRepo.count();
    }

    @Override
    @Transactional(readOnly = true)
    public long countEnabled() {
        return userRepo.countByEnabledTrue();
    }

    @Override
    @Transactional(readOnly = true)
    public long countDisabled() {
        return userRepo.countByEnabledFalse();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findUsernamesByRole(String roleName) {
        return Set.copyOf(userRepo.findUsernamesByRoleName(roleName));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findUsernamesByPermission(String permissionName) {
        return Set.copyOf(userRepo.findUsernamesByPermissionName(permissionName));
    }
}
