package com.cernecommerce.core.ports.out.cashback;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.cashback.CashbackRate;
import com.cernecommerce.core.domain.model.cashback.CashbackScope;

import java.time.Instant;
import java.util.Optional;

/**
 * Port de saída para persistência das taxas de cashback (CRM-F003).
 */
public interface CashbackRateRepository {

    CashbackRate save(CashbackRate rate);

    Optional<CashbackRate> findById(Long id);

    PageResult<CashbackRate> findAll(int page, int size);

    /**
     * Resolve a taxa aplicável a um SKU/categoria no instante informado, na cadeia
     * SKU → CATEGORY → GLOBAL — a regra ativa e vigente mais específica vence.
     */
    Optional<CashbackRate> findApplicable(String sku, String category, Instant at);

    /** A taxa GLOBAL ativa e vigente, ignorando SKU e categoria. */
    Optional<CashbackRate> findGlobal(Instant at);

    /**
     * Indica se já existe uma taxa ativa, sem {@code validTo}, para a mesma abrangência — usado
     * para recusar uma segunda taxa GLOBAL ou uma segunda taxa para a mesma categoria/SKU
     * (mesma abrangência do índice único parcial do schema).
     */
    boolean existsActiveOpenEnded(CashbackScope scope, String scopeRef);
}
