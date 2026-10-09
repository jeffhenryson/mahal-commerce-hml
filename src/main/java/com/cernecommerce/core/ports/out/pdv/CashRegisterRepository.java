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

    /**
     * PDV-C035 — trava exclusiva da sessão até o fim da transação ({@code FOR UPDATE}), relendo a
     * linha. Usada pelo fechamento: nada mais escreve na sessão entre a conferência e a gravação.
     * O {@link #findById} seguinte na mesma transação devolve o estado já relido. Sessão inexistente
     * é ignorada (o {@code findById} é quem responde 404).
     */
    void lockForUpdate(Long id);

    /**
     * PDV-C035 — trava compartilhada ({@code FOR SHARE}) para quem escreve <i>na</i> sessão: venda,
     * movimento, liquidação e mesa. Escritores não se bloqueiam entre si; o fechamento espera todos
     * terminarem, e quem chega depois dele relê CLOSED.
     */
    void lockForShare(Long id);
}
