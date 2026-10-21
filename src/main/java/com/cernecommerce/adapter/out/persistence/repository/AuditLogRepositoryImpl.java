package com.cernecommerce.adapter.out.persistence.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.cernecommerce.adapter.out.persistence.entity.AuditLogEntity;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.model.AuditLogEntry;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.ports.out.audit.AuditLogRepository;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepositoryImpl.class);

    // Static mapper — avoids a Spring-managed ObjectMapper dependency that can be absent
    // in lightweight test contexts (same pattern as LoginRateLimitingFilter).
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final AuditLogJpaRepository jpaRepo;

    public AuditLogRepositoryImpl(AuditLogJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditEvent event, String ipAddress) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setUsername(event.username());
        entity.setAction(event.type().name());
        entity.setTarget(resolveTarget(event));
        entity.setDetails(serializeDetails(event));
        entity.setIpAddress(ipAddress);
        entity.setTimestamp(event.timestamp());
        jpaRepo.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<AuditLogEntry> findFiltered(String username, String action,
                                                   Instant from, Instant to,
                                                   int page, int size,
                                                   Set<String> excludeActions) {
        Specification<AuditLogEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null) predicates.add(cb.equal(root.get("username"), username));
            if (action   != null) predicates.add(cb.equal(root.get("action"), action));
            if (from     != null) predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            if (to       != null) predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            if (excludeActions != null && !excludeActions.isEmpty()) {
                predicates.add(cb.not(root.get("action").in(excludeActions)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<AuditLogEntity> p = jpaRepo.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp")));
        List<AuditLogEntry> content = p.getContent().stream()
                .map(e -> new AuditLogEntry(e.getId(), e.getUsername(), e.getAction(),
                        e.getTarget(), e.getDetails(), e.getIpAddress(), e.getTimestamp()))
                .collect(Collectors.toList());
        return new PageResult<>(content, page, size, p.getTotalElements(), p.getTotalPages());
    }

    private String serializeDetails(AuditEvent event) {
        if (event.details().isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(event.details());
        } catch (JsonProcessingException e) {
            log.warn("audit.details.serialize.failed action={}", event.type(), e);
            return event.details().toString();
        }
    }

    @Override
    @Transactional
    public long deleteOlderThan(Instant cutoff) {
        return jpaRepo.deleteOlderThan(cutoff);
    }

    @Override
    @Transactional(readOnly = true)
    public long countSince(String action, String target, Instant since) {
        return jpaRepo.countByActionAndTargetAndTimestampGreaterThanEqual(action, target, since);
    }

    private String resolveTarget(AuditEvent event) {
        Object role = event.details().get("role");
        if (role != null) return "role:" + role;
        Object permission = event.details().get("permission");
        if (permission != null) return "permission:" + permission;
        Object targetUser = event.details().get("targetUser");
        if (targetUser != null) return "user:" + targetUser;
        // Salão — a recusa da confirmação conta por cliente (trava de 5 erros).
        Object customer = event.details().get("customerId");
        if (customer != null) return "customer:" + customer;
        return null;
    }
}
