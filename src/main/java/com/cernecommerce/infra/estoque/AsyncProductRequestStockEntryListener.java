package com.cernecommerce.infra.estoque;

import com.cernecommerce.core.ports.in.ProductRequestUseCase;
import com.cernecommerce.core.ports.out.estoque.StockEntryListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Seção 7 dos pedidos de clientes: entrada de estoque num SKU vinculado a um pedido {@code ORDERED} move o pedido
 * para {@code ARRIVED}. Assíncrono e em transação própria: o {@code afterCommit} da movimentação não pode abrir
 * escrita na transação que já terminou. Só é ligado com {@code estoque.product-requests.auto-arrive-on-stock-entry}.
 */
@Component
public class AsyncProductRequestStockEntryListener implements StockEntryListener {

    private static final Logger log = LoggerFactory.getLogger(AsyncProductRequestStockEntryListener.class);

    private final ProductRequestUseCase productRequests;

    public AsyncProductRequestStockEntryListener(ProductRequestUseCase productRequests) {
        this.productRequests = productRequests;
    }

    @Override
    @Async("taskExecutor")
    public void onStockEntry(Collection<String> skus) {
        try {
            productRequests.arriveByStockEntry(skus);
        } catch (Exception ex) {
            log.error("product-request.auto-arrive.failed skus={} error={}", skus, ex.getMessage(), ex);
        }
    }
}
