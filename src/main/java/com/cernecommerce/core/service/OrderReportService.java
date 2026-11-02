package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pedido.InvalidReportPeriodException;
import com.cernecommerce.core.domain.model.pedido.MarginSummary;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.OrderSummary;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.OrderReportUseCase;
import com.cernecommerce.core.ports.out.pedido.OrderReportRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class OrderReportService implements OrderReportUseCase {

    private static final int TOP_PRODUCTS_LIMIT = 10;

    /** Teto de intervalo — sem rate limit de verdade no projeto ainda, é a única defesa contra a
     * consulta mais cara do domínio (mesma preocupação registrada, mas não resolvida, no domínio
     * financeiro). */
    private static final long MAX_RANGE_DAYS = 366;

    private final OrderReportRepository orderReportRepository;

    public OrderReportService(OrderReportRepository orderReportRepository) {
        this.orderReportRepository = orderReportRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSummary getSummary(SalesChannel channel, OrderStatus status, Long customerId, String warehouseCode,
            Instant from, Instant to) {
        validatePeriod(from, to);
        return orderReportRepository.summarize(channel, status, customerId, warehouseCode, from, to,
                TOP_PRODUCTS_LIMIT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderSummary.TopProduct> getTopProducts(SalesChannel channel, OrderStatus status,
            Long customerId, Instant from, Instant to, int limit, boolean byQuantity) {
        // from/to omitidos → histórico completo (EST-Vendas: "best seller da loja"). Diferente de
        // getSummary, sem teto de MAX_RANGE_DAYS quando algum lado é omitido — pedir o histórico
        // inteiro é o próprio pedido, não um acidente a defender; o teto só se aplica quando os
        // dois lados vieram explícitos.
        boolean explicitPeriod = from != null && to != null;
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.now();
        if (explicitPeriod) {
            validatePeriod(from, to);
        } else if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidReportPeriodException("'from' não pode ser depois de 'to'");
        }
        return orderReportRepository.findTopProducts(channel, status, customerId, effectiveFrom,
                effectiveTo, limit, byQuantity);
    }

    @Override
    @Transactional(readOnly = true)
    public MarginSummary getMarginReport(SalesChannel channel, String warehouseCode, Instant from, Instant to,
            int limit) {
        validatePeriod(from, to);
        if (limit < 1 || limit > 50) {
            throw new InvalidReportPeriodException("limit deve estar entre 1 e 50");
        }
        return orderReportRepository.summarizeMargin(channel, warehouseCode, from, to, limit);
    }

    /** Package-private: as análises de vendas (SalesAnalyticsService) usam o mesmo teto. */
    static void checkPeriod(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new InvalidReportPeriodException("'from' e 'to' são obrigatórios");
        }
        if (from.isAfter(to)) {
            throw new InvalidReportPeriodException("'from' não pode ser depois de 'to'");
        }
        if (Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            throw new InvalidReportPeriodException("Intervalo máximo permitido: " + MAX_RANGE_DAYS + " dias");
        }
    }

    private void validatePeriod(Instant from, Instant to) {
        checkPeriod(from, to);
    }
}
