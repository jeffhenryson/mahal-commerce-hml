package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.OpenPackageCloseReason;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EST-F037 contra banco: a lata passa da estimativa sem fechar, "Acabou" fecha sem baixa, a próxima
 * sessão abre outra, e o rendimento real sai das latas fechadas com uso — a reposta vazia fica fora.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OpenPackageYieldIT {

    @Autowired EstoqueUseCase estoqueUseCase;

    @Test
    void lataPassaDaEstimativa_acabouFechaSemBaixa_eORendimentoIgnoraALataVazia() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sku = "ZOMO-MENTA-" + s;
        String warehouse = "LOUNGE-" + s;
        String operador = "atendente-" + s;
        estoqueUseCase.createWarehouse(warehouse, "Lounge " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Zomo Menta " + s, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 2, null));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("5"), "carga", operador);

        // Estimativa 2, três sessões: a mesma lata, 3 de 2, uma só SAIDA.
        OpenPackage lata = null;
        for (int i = 0; i < 3; i++) {
            lata = estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        }
        assertThat(lata.uses()).isEqualTo(3);
        assertThat(lata.isOpen()).isTrue();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("4");

        // "Acabou": fecha sem baixa e sem abrir outra.
        OpenPackage acabou = estoqueUseCase.finishOpenPackage(sku, warehouse, operador);
        assertThat(acabou.closeReason()).isEqualTo(OpenPackageCloseReason.FINISHED);
        assertThat(estoqueUseCase.listOpenPackages(warehouse)).isEmpty();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("4");

        // A próxima sessão abre uma nova (SAIDA de 1); repor duas vezes deixa uma reposta vazia.
        estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        estoqueUseCase.replaceOpenPackage(sku, warehouse, operador);
        estoqueUseCase.replaceOpenPackage(sku, warehouse, operador);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("1");

        List<EstoqueUseCase.OpenPackageYield> rendimento = estoqueUseCase.openPackageYield(warehouse, null, null);
        assertThat(rendimento).singleElement().satisfies(y -> {
            assertThat(y.sku()).isEqualTo(sku);
            assertThat(y.closedCount()).as("FINISHED (3) e REPLACED (1); a reposta vazia fica fora").isEqualTo(2);
            assertThat(y.totalSessions()).isEqualTo(4);
            assertThat(y.avgSessions()).isEqualByComparingTo("2.0");
            assertThat(y.estimate()).isEqualTo(2);
        });

        Instant amanha = Instant.now().plus(1, ChronoUnit.DAYS);
        assertThat(estoqueUseCase.openPackageYield(warehouse, amanha, null)).isEmpty();
    }
}
