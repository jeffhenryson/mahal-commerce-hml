package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSessionFilter;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Port de saída para persistência de sessões de caixa do PDV.
 */
public interface CashRegisterRepository {

    PageResult<CashRegisterSession> findAll(int page, int size);

    /** PDV-F026 — listagem filtrada, mais recentes primeiro ({@code openedAt DESC, id DESC}). */
    PageResult<CashRegisterSession> findAll(CashRegisterSessionFilter filter, int page, int size);

    Optional<CashRegisterSession> findOpenByOperator(String operator);

    Optional<CashRegisterSession> findById(Long id);

    /** PED-F003 — operador de cada sessão, em lote (lista de pedidos). Ids inexistentes ficam fora do mapa. */
    Map<Long, String> findOperatorsByIds(Collection<Long> ids);

    CashRegisterSession save(CashRegisterSession session);
}
