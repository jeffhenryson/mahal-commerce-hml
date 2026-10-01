package com.cernecommerce.core.ports.out.recebivel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Limite de crédito individual do "Marcar" (CRM-F010). Sem registro, vale o limite padrão. */
public interface CustomerCreditLimitRepository {

    Optional<BigDecimal> findByCustomerId(Long customerId);

    Map<Long, BigDecimal> findByCustomerIds(Collection<Long> customerIds);

    void save(Long customerId, BigDecimal creditLimit, String updatedBy, Instant updatedAt);

    void delete(Long customerId);
}
