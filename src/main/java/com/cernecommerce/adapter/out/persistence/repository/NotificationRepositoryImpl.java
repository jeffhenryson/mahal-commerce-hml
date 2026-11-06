package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.NotificationEntity;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.notification.Notification;
import com.cernecommerce.core.domain.model.notification.NotificationType;
import com.cernecommerce.core.ports.out.notification.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NotificationJpaRepository jpaRepo;

    public NotificationRepositoryImpl(NotificationJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification save(Notification notification) {
        NotificationEntity entity = toEntity(notification);
        return toDomain(jpaRepo.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Notification> findByUsername(String username, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<NotificationEntity> result = unreadOnly
                ? jpaRepo.findUnreadByUsername(username, pageable)
                : jpaRepo.findByUsername(username, pageable);
        List<Notification> content = result.getContent().stream().map(this::toDomain).toList();
        return new PageResult<>(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Override
    @Transactional
    public void markAsRead(Long id, String username) {
        jpaRepo.markAsRead(id, username, Instant.now());
    }

    @Override
    @Transactional
    public void markAllAsRead(String username) {
        jpaRepo.markAllAsRead(username, Instant.now());
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(String username) {
        return jpaRepo.countByUsernameAndReadAtIsNull(username);
    }

    @Override
    @Transactional
    public void delete(Long id, String username) {
        jpaRepo.deleteByIdAndUsername(id, username);
    }

    @Override
    @Transactional
    public void deleteReadBefore(Instant before) {
        jpaRepo.deleteReadBefore(before);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<Notification> findById(Long id, String username) {
        return jpaRepo.findById(id).filter(e -> username.equals(e.getUsername())).map(this::toDomain);
    }

    private static String toJson(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Mapa da notificação não serializa", ex);
        }
    }

    private static Map<String, String> fromJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Mapa da notificação ilegível", ex);
        }
    }

    private NotificationEntity toEntity(Notification n) {
        NotificationEntity e = new NotificationEntity();
        e.setId(n.id());
        e.setUsername(n.username());
        e.setType(n.type().name());
        e.setTitle(n.title());
        e.setBody(n.body());
        e.setTargetUrl(n.targetUrl());
        e.setReadAt(n.readAt());
        e.setCreatedAt(n.createdAt());
        e.setDetails(toJson(n.details()));
        e.setMetadata(toJson(n.metadata()));
        return e;
    }

    private Notification toDomain(NotificationEntity e) {
        return new Notification(
                e.getId(),
                e.getUsername(),
                NotificationType.valueOf(e.getType()),
                e.getTitle(),
                e.getBody(),
                e.getTargetUrl(),
                e.getReadAt(),
                e.getCreatedAt(),
                fromJson(e.getDetails()),
                fromJson(e.getMetadata())
        );
    }
}
