package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.CashRegisterSessionClosedException;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-C035 — o fechamento do caixa lê, confere e grava com a sessão travada. Sem a trava, dois
 * fechamentos simultâneos liam os dois "OPEN", calculavam o esperado e o último a gravar vencia:
 * o contado e a diferença de um deles sumiam sem erro. Com a trava, o segundo espera o primeiro
 * e lê CLOSED.
 *
 * <p>Mesmo molde de {@link PdvSaleConcurrencyIT}: threads liberadas juntas por um latch.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CashSessionCloseConcurrencyIT {

    private static final int THREADS = 4;

    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @Test
    void concurrentCloses_onlyOneWins_andTheOthersSeeTheSessionClosed() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String warehouseCode = "CONC_CLOSE_WH_" + suffix;
        String operator = "caixa-close-" + suffix;
        estoqueUseCase.createWarehouse(warehouseCode, "Loja de concorrência", WarehouseType.LOJA_FISICA);
        CashRegisterSession session = pdvUseCase.openSession(operator, new BigDecimal("100.00"), warehouseCode);

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger alreadyClosed = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            BigDecimal counted = new BigDecimal(100 + i);
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                try {
                    pdvUseCase.closeSession(session.id(), counted, null, operator, false);
                    closed.incrementAndGet();
                } catch (CashRegisterSessionClosedException e) {
                    alreadyClosed.incrementAndGet();
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

        assertThat(closed.get()).as("exatamente um fechamento grava").isEqualTo(1);
        assertThat(alreadyClosed.get()).as("os demais leem a sessão já fechada").isEqualTo(THREADS - 1);
        CashRegisterSession reread = pdvUseCase.getSession(session.id());
        assertThat(reread.status()).isEqualTo(CashRegisterSession.Status.CLOSED);
    }
}
