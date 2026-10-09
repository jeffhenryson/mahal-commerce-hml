package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.PdvUseCase.SaleItemCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-F043 — a mesma venda (mesma chave) enviada por 5 threads ao mesmo tempo gera exatamente um
 * pedido e uma baixa de estoque. As que perdem a corrida colidem no índice único da chave, revertem
 * inteiras, e o reenvio seguinte encontra a venda que entrou.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OfflineSaleConcurrencyIT {

    private static final int THREADS = 5;

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired PdvUseCase pdvUseCase;

    @Test
    void mesmaChaveEmParalelo_geraUmPedidoSo() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String warehouse = "CONC-OFF-" + suffix;
        String sku = "CONC-OFF-SKU-" + suffix;
        String operator = "caixa-" + suffix;
        estoqueUseCase.createWarehouse(warehouse, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Piteira " + suffix, "Piteiras", List.of(),
                Pricing.of(new BigDecimal("1.00"), null, new BigDecimal("2.00")));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("50"), "carga", operator);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        String chave = UUID.randomUUID().toString();

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        Set<Long> orderIds = ConcurrentHashMap.newKeySet();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    orderIds.add(pdvUseCase.registerSaleIdempotent(caixa.id(), null,
                            List.of(new SaleItemCommand(sku, BigDecimal.ONE, null)),
                            List.of(new PaymentCommand(PaymentMethod.DINHEIRO, new BigDecimal("2.00"), null)),
                            operator, false, null, chave, null).order().id());
                } catch (RuntimeException e) {
                    // Perdeu a corrida na chave (ou no @Version do saldo): a venda reverteu inteira.
                }
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        // O reenvio depois da corrida encontra a venda que entrou.
        Long registrada = pdvUseCase.findOrderIdByClientSaleId(chave).orElseThrow();
        assertThat(orderIds).as("toda thread que respondeu viu o mesmo pedido").allMatch(registrada::equals);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity())
                .as("uma venda só baixou estoque").isEqualByComparingTo("49");
    }
}
