package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.SortDirection;
import com.cernecommerce.core.domain.model.estoque.ProductRequest;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEntry;
import com.cernecommerce.core.domain.model.estoque.ProductRequestEvent;
import com.cernecommerce.core.domain.model.estoque.ProductRequestSort;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStats;
import com.cernecommerce.core.domain.model.estoque.ProductRequestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port de saída dos pedidos de clientes (V208): item, solicitações e histórico. */
public interface ProductRequestRepository {

    /**
     * Insere (id nulo) ou atualiza conferindo {@code version}.
     *
     * @throws com.cernecommerce.core.domain.exception.estoque.ProductRequestConflictException outra transação
     *         alterou o item antes
     */
    ProductRequest save(ProductRequest request);

    Optional<ProductRequest> findById(Long id);

    List<ProductRequest> findByIds(Collection<Long> ids);

    ProductRequestEntry saveEntry(ProductRequestEntry entry);

    /** Solicitações do item, ativas primeiro e depois por criação. */
    List<ProductRequestEntry> findEntries(Long requestId);

    ProductRequestEvent saveEvent(ProductRequestEvent event);

    /** Histórico do item, mais recentes primeiro. */
    List<ProductRequestEvent> findEvents(Long requestId);

    /** Página de itens com os agregados das solicitações ativas. {@code q} já vem normalizado. */
    PageResult<Row> search(Collection<ProductRequestStatus> statuses, String normalizedQuery, Long categoryId,
            ProductRequestSort sort, SortDirection direction, int page, int size);

    /** Agregados das solicitações ativas por item (itens sem solicitação ativa ficam fora do mapa). */
    Map<Long, ProductRequestStats> statsFor(Collection<Long> requestIds);

    /** Itens abertos (PENDING/ORDERED/ARRIVED) — base da sugestão de itens parecidos. */
    List<ProductRequest> findOpen();

    Map<ProductRequestStatus, Long> countByStatus();

    /** Itens em {@code ARRIVED} com ao menos uma solicitação ativa ainda sem aviso. */
    long countArrivedAwaitingNotification();

    /** Itens abertos com mais solicitações ativas. */
    List<Row> topRequested(int limit);

    /** Itens em que o cliente tem alguma solicitação (qualquer status), mais recentes primeiro. */
    List<ProductRequest> findByCustomer(Long customerId);

    /** Itens {@code ORDERED} vinculados a algum dos SKUs — gatilho de entrada de estoque. */
    List<ProductRequest> findOrderedByLinkedSkus(Collection<String> skus);

    record Row(ProductRequest request, ProductRequestStats stats) {
    }
}
