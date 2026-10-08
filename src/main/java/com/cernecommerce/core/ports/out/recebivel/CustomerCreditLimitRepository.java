package com.cernecommerce.core.ports.out.recebivel;

import com.cernecommerce.core.domain.model.recebivel.CreditLimits;
import com.cernecommerce.core.domain.model.recebivel.OnAccountChannel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Limites de crédito individuais do "Marcar" (CRM-F010), um por cliente e canal. {@code channel}
 * nulo é o teto total. Sem registro, vale o padrão (no canal) ou não há teto (no total).
 */
public interface CustomerCreditLimitRepository {

    /** Nunca nulo: sem nenhuma linha, {@link CreditLimits#none()}. */
    CreditLimits findByCustomerId(Long customerId);

    /** Só os clientes com alguma linha. */
    Map<Long, CreditLimits> findByCustomerIds(Collection<Long> customerIds);

    void save(Long customerId, OnAccountChannel channel, BigDecimal creditLimit, String updatedBy, Instant updatedAt);

    void delete(Long customerId, OnAccountChannel channel);
}
