package com.cernecommerce.core.ports.out.ecommerce;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoPedidoApp;
import com.cernecommerce.core.domain.model.ecommerce.AtendimentoStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Atendimento no caixa dos pedidos do app (ECM-F010). */
public interface AtendimentoPedidoAppRepository {

    AtendimentoPedidoApp save(AtendimentoPedidoApp atendimento);

    Optional<AtendimentoPedidoApp> findByOrderId(Long orderId);

    /**
     * Com trava de escrita na linha: dois operadores aceitando o mesmo pedido ao mesmo tempo, ou o
     * varredor expirando enquanto o caixa liquida, passam um de cada vez.
     */
    Optional<AtendimentoPedidoApp> findByOrderIdForUpdate(Long orderId);

    /** Em lote, para enriquecer uma página de pedidos sem uma consulta por linha. */
    List<AtendimentoPedidoApp> findByOrderIds(Collection<Long> orderIds);

    /**
     * A fila do PDV, do mais antigo ao mais novo. {@code sessionId} nulo traz de todos os caixas
     * (e os pendentes, que ainda não têm caixa).
     */
    PageResult<AtendimentoPedidoApp> findByStatus(Set<AtendimentoStatus> statuses, Long sessionId, int page, int size);

    /** Ids dos em andamento com prazo vencido — o que o varredor cancela. */
    List<Long> findVencidos(Instant agora, int limite);
}
