package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.CashSessionAdjustmentEntity;
import com.cernecommerce.adapter.out.persistence.entity.OrderPaymentCorrectionEntity;
import com.cernecommerce.core.domain.model.pagamento.CashSessionAdjustment;
import com.cernecommerce.core.domain.model.pagamento.OrderPaymentCorrection;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentCorrectionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class OrderPaymentCorrectionRepositoryImpl implements OrderPaymentCorrectionRepository {

    private final OrderPaymentCorrectionJpaRepository correctionJpaRepository;
    private final CashSessionAdjustmentJpaRepository adjustmentJpaRepository;

    public OrderPaymentCorrectionRepositoryImpl(OrderPaymentCorrectionJpaRepository correctionJpaRepository,
            CashSessionAdjustmentJpaRepository adjustmentJpaRepository) {
        this.correctionJpaRepository = correctionJpaRepository;
        this.adjustmentJpaRepository = adjustmentJpaRepository;
    }

    @Override
    public OrderPaymentCorrection save(OrderPaymentCorrection correction) {
        OrderPaymentCorrectionEntity entity = new OrderPaymentCorrectionEntity(correction.id(),
                correction.orderId(), correction.reason(), correction.correctedBy(), correction.correctedAt(),
                correction.cashSessionId(), correction.sessionWasClosed());
        return toDomain(correctionJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPaymentCorrection> findByOrderId(Long orderId) {
        return correctionJpaRepository.findByOrderIdOrderByIdAsc(orderId).stream().map(this::toDomain).toList();
    }

    @Override
    public CashSessionAdjustment saveAdjustment(CashSessionAdjustment adjustment) {
        CashSessionAdjustmentEntity saved = adjustmentJpaRepository.save(new CashSessionAdjustmentEntity(
                adjustment.id(), adjustment.sessionId(), adjustment.orderId(), adjustment.correctionId(),
                adjustment.method().name(), adjustment.deltaAmount(), adjustment.createdBy(),
                adjustment.createdAt()));
        return new CashSessionAdjustment(saved.getId(), saved.getSessionId(), saved.getOrderId(),
                saved.getCorrectionId(), PaymentMethod.valueOf(saved.getMethod()), saved.getDeltaAmount(),
                saved.getCreatedBy(), saved.getCreatedAt());
    }

    private OrderPaymentCorrection toDomain(OrderPaymentCorrectionEntity e) {
        return new OrderPaymentCorrection(e.getId(), e.getOrderId(), e.getReason(), e.getCorrectedBy(),
                e.getCorrectedAt(), e.getCashSessionId(), e.isSessionWasClosed());
    }
}
