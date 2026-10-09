package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EST-C025 — o contador da lata aberta sob escrita simultânea.
 *
 * <p>{@code consumeSession} é read-modify-write ({@code uses + 1}). Sem trava, dois atendentes
 * lançando sessão do mesmo sabor ao mesmo tempo leem o mesmo {@code uses} e gravam o mesmo
 * resultado: um uso some e a lata rende uma sessão a mais do que a realidade. A correção é a trava
 * <b>pessimista</b> na leitura da lata, como a da comanda (PDV-C008): as escritas fazem fila e
 * <b>todas passam</b>, em vez de uma tomar 409 no meio do salão.</p>
 *
 * <p>Sem {@code @Transactional} de classe: cada thread precisa da própria transação para a trava
 * entrar em jogo — mesmo padrão de {@link KitSaleConcurrencyIT}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenPackageConcurrencyIT {

    private static final int THREADS = 8;
    private static final int SESSIONS_PER_UNIT = 20;

    @Autowired EstoqueUseCase estoqueUseCase;

    @Test
    void concurrentSessions_onTheSamePackage_neverLoseAUse() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String warehouseCode = "CONC_LATA_WH_" + suffix;
        String sku = "CONC_LATA_" + suffix;
        String operator = "atendente-" + suffix;

        estoqueUseCase.createWarehouse(warehouseCode, "Lounge de concorrência", WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Essência de concorrência", "Essências", List.of(),
                Pricing.of(new BigDecimal("10.00"), null, new BigDecimal("25.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, SESSIONS_PER_UNIT, new BigDecimal("60.00")));
        estoqueUseCase.adjustStock(sku, warehouseCode, MovementType.ENTRADA, new BigDecimal("10.000"),
                "carga inicial", operator);
        // Lata cheia já aberta (EST-F033): com folga para as 8 sessões, nenhuma thread precisa
        // abrir outra — o teste isola a corrida sobre o CONTADOR, não sobre a abertura.
        estoqueUseCase.registerOpenPackage(sku, warehouseCode, SESSIONS_PER_UNIT, operator);

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                estoqueUseCase.consumeSession(sku, warehouseCode, BigDecimal.ONE, operator);
                return null;
            }));
        }
        ready.await();
        start.countDown();
        for (Future<?> f : futures) {
            // Propaga qualquer falha: com trava pessimista nenhuma thread pode perder a corrida.
            f.get();
        }
        executor.shutdown();

        assertThat(estoqueUseCase.findOpenPackage(sku, warehouseCode).uses())
                .as("%d sessões simultâneas na mesma lata: o contador tem que registrar todas", THREADS)
                .isEqualTo(THREADS);
        // A lata já estava aberta: nenhuma das sessões tira unidade da prateleira.
        assertThat(estoqueUseCase.getStockBalance(sku, warehouseCode).quantity())
                .isEqualByComparingTo("10.000");
    }
}
