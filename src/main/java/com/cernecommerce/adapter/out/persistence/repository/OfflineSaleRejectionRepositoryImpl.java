package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OfflineSaleRejectionEntity;
import com.cernecommerce.core.domain.model.pdv.OfflineRejectionResolution;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleItem;
import com.cernecommerce.core.domain.model.pdv.OfflineSalePayment;
import com.cernecommerce.core.domain.model.pdv.OfflineSaleRejection;
import com.cernecommerce.core.ports.out.pdv.OfflineSaleRejectionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OfflineSaleRejectionRepositoryImpl implements OfflineSaleRejectionRepository {

    // Mapper estático, mesmo motivo de AuditLogRepositoryImpl: o do Spring pode faltar em contexto leve.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** O que vai em {@code payload}: a venda como chegou, para o reenvio. */
    private record Payload(List<OfflineSaleItem> items, List<OfflineSalePayment> payments) {
    }

    private final OfflineSaleRejectionJpaRepository jpaRepository;

    public OfflineSaleRejectionRepositoryImpl(OfflineSaleRejectionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OfflineSaleRejection save(OfflineSaleRejection r) {
        OfflineSaleRejectionEntity e = r.id() == null
                ? new OfflineSaleRejectionEntity()
                : jpaRepository.findById(r.id()).orElseGet(OfflineSaleRejectionEntity::new);
        e.setId(r.id());
        e.setSessionId(r.sessionId());
        e.setClientSaleId(r.clientSaleId());
        e.setClientSoldAt(r.clientSoldAt());
        e.setCustomerId(r.customerId());
        e.setPayload(write(new Payload(r.items(), r.payments())));
        e.setErrorCode(r.errorCode());
        e.setMessage(truncate(r.message(), 500));
        e.setCreatedAt(r.createdAt());
        e.setCreatedBy(r.createdBy());
        e.setResolvedAt(r.resolvedAt());
        e.setResolvedBy(r.resolvedBy());
        e.setResolution(r.resolution() == null ? null : r.resolution().name());
        e.setResolutionNote(truncate(r.resolutionNote(), 255));
        e.setOrderId(r.orderId());
        return toDomain(jpaRepository.save(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfflineSaleRejection> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OfflineSaleRejection> findByClientSaleId(String clientSaleId) {
        return jpaRepository.findByClientSaleId(clientSaleId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfflineSaleRejection> findBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdOrderByIdDesc(sessionId).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findPendingIdsBySessionId(Long sessionId) {
        return jpaRepository.findPendingIds(sessionId);
    }

    private OfflineSaleRejection toDomain(OfflineSaleRejectionEntity e) {
        Payload payload = read(e.getPayload());
        return new OfflineSaleRejection(e.getId(), e.getSessionId(), e.getClientSaleId(), e.getClientSoldAt(),
                e.getCustomerId(), payload.items(), payload.payments(), e.getErrorCode(), e.getMessage(),
                e.getCreatedAt(), e.getCreatedBy(), e.getResolvedAt(), e.getResolvedBy(),
                e.getResolution() == null ? null : OfflineRejectionResolution.valueOf(e.getResolution()),
                e.getResolutionNote(), e.getOrderId());
    }

    private static String write(Payload payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("não foi possível serializar a venda offline", ex);
        }
    }

    private static Payload read(String json) {
        try {
            return MAPPER.readValue(json, Payload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("payload de venda offline ilegível", ex);
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
