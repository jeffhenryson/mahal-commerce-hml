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
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Limite da lata sem troca automática (2026-11-13), contra o banco: passar do limite só soma usos,
 * a V204 desliga quem tinha a troca ligada, a correção de usos não mexe em saldo, e o histórico
 * lista só as fechadas, mais recente primeiro, com filtro de dias e paginação.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class OpenPackageAutoReplaceIT {

    private static final ZoneId ZONA_LOJA = ZoneId.of("America/Sao_Paulo");

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired JdbcTemplate jdbc;

    @Test
    void passarDoLimite_soSomaUsos_aCorrecaoNaoMexeNoSaldo_eOHistoricoListaAsFechadas() throws Exception {
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

        // Front antigo pede para ligar: grava só o limite.
        estoqueUseCase.setAutoReplace(sku, true, 5);
        assertThat(estoqueUseCase.findProductBySku(sku).autoReplace()).isFalse();
        assertThat(estoqueUseCase.findProductBySku(sku).autoReplaceLimit()).isEqualTo(5);

        // Dado legado, ligado antes da mudança: a V204 desliga e mantém o limite.
        jdbc.update("UPDATE product SET auto_replace = TRUE WHERE sku = ?", sku);
        jdbc.execute(new ClassPathResource("db/migration/V204__product_auto_replace_off.sql")
                .getContentAsString(StandardCharsets.UTF_8));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE auto_replace = TRUE", Integer.class))
                .isZero();
        assertThat(estoqueUseCase.findProductBySku(sku).autoReplaceLimit()).isEqualTo(5);

        // A 1ª sessão abre a lata (SAIDA 1 → saldo 1). A 6ª passa do limite 5: uses = 6, lata aberta, sem baixa.
        OpenPackage lata = null;
        for (int i = 0; i < 6; i++) {
            lata = estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        }
        assertThat(lata.uses()).isEqualTo(6);
        assertThat(lata.isOpen()).isTrue();
        assertThat(lata.isExhausted()).isTrue();
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("1");

        // O gerente corrige para 3: exhausted recalculado, saldo intocado.
        OpenPackage corrigida = estoqueUseCase.adjustOpenPackageUses(sku, warehouse, 3, "Correção no PDV", "gerente");
        assertThat(corrigida.uses()).isEqualTo(3);
        assertThat(corrigida.isExhausted()).isFalse();
        assertThat(estoqueUseCase.findOpenPackage(sku, warehouse).uses()).isEqualTo(3);
        assertThat(estoqueUseCase.getStockBalance(sku, warehouse).quantity()).isEqualByComparingTo("1");

        // "Repor" fecha a primeira (SAIDA 1 → saldo 0); "Acabou" fecha a segunda.
        estoqueUseCase.replaceOpenPackage(sku, warehouse, operador);
        estoqueUseCase.consumeSession(sku, warehouse, BigDecimal.ONE, operador);
        estoqueUseCase.finishOpenPackage(sku, warehouse, operador);
        PageResult<OpenPackage> historico = estoqueUseCase.closedPackageHistory(sku, warehouse, null, null, 0, 50);
        assertThat(historico.totalElements()).isEqualTo(2);
        assertThat(historico.content()).extracting(OpenPackage::closeReason)
                .containsExactly(OpenPackageCloseReason.FINISHED, OpenPackageCloseReason.REPLACED);

        // Paginação e filtro de dias (inclusivos, no fuso da loja).
        PageResult<OpenPackage> pagina = estoqueUseCase.closedPackageHistory(sku, warehouse, null, null, 1, 1);
        assertThat(pagina.content()).singleElement()
                .extracting(OpenPackage::closeReason).isEqualTo(OpenPackageCloseReason.REPLACED);
        assertThat(pagina.totalPages()).isEqualTo(2);
        LocalDate hoje = LocalDate.now(ZONA_LOJA);
        assertThat(estoqueUseCase.closedPackageHistory(sku, warehouse, hoje, hoje, 0, 50).totalElements())
                .isEqualTo(2);
        assertThat(estoqueUseCase.closedPackageHistory(sku, warehouse, hoje.plusDays(1), null, 0, 50)
                .content()).isEmpty();
    }
}
