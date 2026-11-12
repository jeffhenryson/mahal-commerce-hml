package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.PageResult;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Repor sozinha" e histórico de latas (V202) contra o banco: a CHECK nova aceita AUTO_REPLACED, a
 * troca baixa 1 na mesma transação da sessão, sem saldo a sessão passa, e o histórico lista só as
 * fechadas, mais recente primeiro, com filtro de dias e paginação.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OpenPackageAutoReplaceIT {

    private static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");

    @Autowired EstoqueUseCase estoqueUseCase;

    @Test
    void lataTrocaSozinhaNoLimite_semSaldoASessaoPassa_eOHistoricoListaAsFechadas() {
        String s = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String sku = "ZOMO-UVA-" + s;
        String warehouse = "LOUNGE-" + s;
        String operador = "atendente-" + s;
        estoqueUseCase.createWarehouse(warehouse, "Lounge " + s, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createProduct(sku, "Zomo Uva " + s, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 5, null));
        estoqueUseCase.adjustStock(sku, warehouse, MovementType.ENTRADA, new BigDecimal("2"), "carga", operador);
        estoqueUseCase.setAutoReplace(sku, true, 2);
        assertThat(estoqueUseCase.findProductBySku(sku).autoReplaceLimit()).isEqualTo(2);

        // 1ª sessão abre a lata (SAIDA 1 → saldo 1); a 2ª atinge o limite e troca (SAIDA 1 → saldo 0).
        estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        OpenPackage trocada = estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        assertThat(trocada.closeReason()).isEqualTo(OpenPackageCloseReason.AUTO_REPLACED);
        assertThat(trocada.uses()).isEqualTo(2);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("0");
        assertThat(estoqueUseCase.findOpenPackage(sku, warehouse).uses()).isZero();

        // A nova chega ao limite sem saldo: a sessão passa, a lata continua aberta e nada sai.
        estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        OpenPackage semSaldo = estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        assertThat(semSaldo.isOpen()).isTrue();
        assertThat(semSaldo.uses()).isEqualTo(2);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("0");

        // "Acabou" fecha a segunda; o histórico traz as duas, a mais recente primeiro.
        estoqueUseCase.finishOpenPackage(sku, warehouse, operador);
        PageResult<OpenPackage> historico = estoqueUseCase.closedPackageHistory(sku, warehouse, null, null, 0, 50);
        assertThat(historico.totalElements()).isEqualTo(2);
        assertThat(historico.content()).extracting(OpenPackage::closeReason)
                .containsExactly(OpenPackageCloseReason.FINISHED, OpenPackageCloseReason.AUTO_REPLACED);

        // Paginação e filtro de dias (inclusivos, no fuso da loja).
        PageResult<OpenPackage> pagina = estoqueUseCase.closedPackageHistory(sku, warehouse, null, null, 1, 1);
        assertThat(pagina.content()).singleElement()
                .extracting(OpenPackage::closeReason).isEqualTo(OpenPackageCloseReason.AUTO_REPLACED);
        assertThat(pagina.totalPages()).isEqualTo(2);
        LocalDate hoje = LocalDate.now(ZONA_LOJA);
        assertThat(estoqueUseCase.closedPackageHistory(sku, warehouse, hoje, hoje, 0, 50).totalElements())
                .isEqualTo(2);
        assertThat(estoqueUseCase.closedPackageHistory(sku, warehouse, hoje.plusDays(1), null, 0, 50)
                .content()).isEmpty();
    }
}
