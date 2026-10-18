package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.adapter.out.persistence.entity.OrderPaymentEntity;
import com.cernecommerce.core.domain.model.pagamento.OrderPayment;
import com.cernecommerce.core.domain.model.pagamento.PaymentChannel;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pagamento.PaymentProvider;
import com.cernecommerce.core.domain.model.pagamento.PaymentStatus;
import com.cernecommerce.core.ports.out.pagamento.OrderPaymentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class OrderPaymentRepositoryImpl implements OrderPaymentRepository {

    private final OrderPaymentJpaRepository orderPaymentJpaRepository;

    public OrderPaymentRepositoryImpl(OrderPaymentJpaRepository orderPaymentJpaRepository) {
        this.orderPaymentJpaRepository = orderPaymentJpaRepository;
    }

    @Override
    public OrderPayment save(OrderPayment payment) {
        OrderPaymentEntity entity = new OrderPaymentEntity();
        entity.setId(payment.id());
        entity.setOrderId(payment.orderId());
        entity.setMethod(payment.method().name());
        entity.setAmount(payment.amount());
        entity.setStatus(payment.status().name());
        entity.setInstallments(payment.installments());
        entity.setGatewayRef(payment.gatewayRef());
        entity.setAuthorizedAt(payment.authorizedAt());
        entity.setCapturedAt(payment.capturedAt());
        entity.setCreatedAt(payment.createdAt());
        entity.setChannel(payment.channel() == null ? null : payment.channel().name());
        entity.setProvider(payment.provider() == null ? null : payment.provider().name());
        entity.setCorrectionId(payment.correctionId());
        entity.setOriginCorrectionId(payment.originCorrectionId());
        entity.setCorrectedAt(payment.correctedAt());
        entity.setCorrectedBy(payment.correctedBy());
        entity.setDueDate(payment.dueDate());
        entity.setRefundSessionId(payment.refundSessionId());
        entity.setCaptureSessionId(payment.captureSessionId());
        return toDomain(orderPaymentJpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderPayment> findByOrderId(Long orderId) {
        return orderPaymentJpaRepository.findByOrderIdOrderByIdAsc(orderId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<OrderPayment>> findByOrderIds(Collection<Long> orderIds) {
        Map<Long, List<OrderPayment>> byOrder = new LinkedHashMap<>();
        if (orderIds == null || orderIds.isEmpty()) {
            return byOrder;
        }
        for (OrderPaymentEntity entity : orderPaymentJpaRepository.findByOrderIdInOrderByIdAsc(orderIds)) {
            byOrder.computeIfAbsent(entity.getOrderId(), k -> new ArrayList<>()).add(toDomain(entity));
        }
        return byOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumCapturedAmountBySessionIdAndMethod(Long sessionId, PaymentMethod method) {
        // PED-F006 — mesma divisão do estorno (PDV-C049): a diferença de troca conta onde foi paga.
        return orZero(orderPaymentJpaRepository.sumCapturedAmountBySessionIdAndMethod(sessionId, method.name()))
                .add(orZero(orderPaymentJpaRepository.sumCapturedInSessionAndMethod(sessionId, method.name())));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumRefundedAmountBySessionIdAndMethod(Long sessionId, PaymentMethod method) {
        // PDV-C049 — duas consultas que usam índice, no lugar de um COALESCE que varria tudo.
        return orZero(orderPaymentJpaRepository.sumRefundedInSessionAndMethod(sessionId, method.name()))
                .add(orZero(orderPaymentJpaRepository.sumRefundedWithoutSessionByOrderSessionAndMethod(sessionId,
                        method.name())));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<PaymentMethod, BigDecimal> sumCapturedBySessionIdGroupedByMethod(Long sessionId) {
        Map<PaymentMethod, BigDecimal> sums = byMethod(
                orderPaymentJpaRepository.sumCapturedBySessionIdGroupByMethod(sessionId));
        byMethod(orderPaymentJpaRepository.sumCapturedInSessionGroupByMethod(sessionId))
                .forEach((method, amount) -> sums.merge(method, amount, BigDecimal::add));
        return sums;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<PaymentMethod, BigDecimal> sumRefundedBySessionIdGroupedByMethod(Long sessionId) {
        Map<PaymentMethod, BigDecimal> sums = byMethod(
                orderPaymentJpaRepository.sumRefundedInSessionGroupByMethod(sessionId));
        byMethod(orderPaymentJpaRepository.sumRefundedWithoutSessionByOrderSessionGroupByMethod(sessionId))
                .forEach((method, amount) -> sums.merge(method, amount, BigDecimal::add));
        return sums;
    }

    /** Linhas {@code [method, soma]} → mapa; método ausente significa zero. */
    static Map<PaymentMethod, BigDecimal> byMethod(List<Object[]> rows) {
        Map<PaymentMethod, BigDecimal> sums = new java.util.EnumMap<>(PaymentMethod.class);
        for (Object[] row : rows) {
            sums.merge(PaymentMethod.valueOf((String) row[0]), orZero((BigDecimal) row[1]), BigDecimal::add);
        }
        return sums;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal sumOnAccountAmountBySessionId(Long sessionId) {
        BigDecimal sum = orderPaymentJpaRepository.sumOnAccountAmountBySessionId(sessionId);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPayment> findByGatewayRef(String gatewayRef) {
        return orderPaymentJpaRepository.findByGatewayRef(gatewayRef).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<PaymentMethod>> findCapturedMethodsByOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<PaymentMethod>> result = new LinkedHashMap<>();
        for (Object[] row : orderPaymentJpaRepository.findCapturedMethodsByOrderIds(orderIds)) {
            result.computeIfAbsent((Long) row[0], k -> new ArrayList<>()).add(PaymentMethod.valueOf((String) row[1]));
        }
        return result;
    }

    private OrderPayment toDomain(OrderPaymentEntity e) {
        return OrderPayment.of(e.getId(), e.getOrderId(), PaymentMethod.valueOf(e.getMethod()), e.getAmount(),
                PaymentStatus.valueOf(e.getStatus()), e.getInstallments(), e.getGatewayRef(),
                e.getAuthorizedAt(), e.getCapturedAt(), e.getCreatedAt(),
                e.getChannel() == null ? null : PaymentChannel.valueOf(e.getChannel()),
                e.getProvider() == null ? null : PaymentProvider.valueOf(e.getProvider()),
                e.getCorrectionId(), e.getOriginCorrectionId(), e.getCorrectedAt(), e.getCorrectedBy(),
                e.getDueDate(), e.getRefundSessionId(), e.getCaptureSessionId());
    }
}
