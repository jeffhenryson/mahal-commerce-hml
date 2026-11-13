package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OffSystemReceiptEntity;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pdv.OffSystemReceipt;
import com.cernecommerce.core.ports.out.pdv.OffSystemReceiptRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OffSystemReceiptRepositoryImpl implements OffSystemReceiptRepository {

    private final OffSystemReceiptJpaRepository jpaRepository;

    public OffSystemReceiptRepositoryImpl(OffSystemReceiptJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OffSystemReceipt save(OffSystemReceipt receipt) {
        OffSystemReceiptEntity entity = new OffSystemReceiptEntity();
        entity.setId(receipt.id());
        entity.setSessionId(receipt.sessionId());
        entity.setAmount(receipt.amount());
        entity.setMethod(receipt.method().name());
        entity.setProvider(receipt.provider() == null ? null : receipt.provider().name());
        entity.setNote(receipt.note());
        entity.setUsername(receipt.username());
        entity.setCreatedAt(receipt.createdAt());
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OffSystemReceipt> findBySessionId(Long sessionId) {
        return jpaRepository.findBySessionIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(sessionId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OffSystemReceipt> findActive(Long sessionId, Long receiptId) {
        return jpaRepository.findByIdAndSessionIdAndDeletedAtIsNull(receiptId, sessionId).map(this::toDomain);
    }

    @Override
    public void markDeleted(Long receiptId, String deletedBy, Instant deletedAt) {
        jpaRepository.findById(receiptId).ifPresent(entity -> {
            entity.setDeletedAt(deletedAt);
            entity.setDeletedBy(deletedBy);
            jpaRepository.save(entity);
        });
    }

    private OffSystemReceipt toDomain(OffSystemReceiptEntity e) {
        return new OffSystemReceipt(e.getId(), e.getSessionId(), e.getAmount(), PaymentMethod.valueOf(e.getMethod()),
                e.getProvider() == null ? null : PaymentProvider.valueOf(e.getProvider()), e.getNote(),
                e.getUsername(), e.getCreatedAt());
    }
}
