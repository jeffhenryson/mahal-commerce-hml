package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.estoque.InsufficientStockException;
import com.cernecommerce.core.domain.exception.estoque.SalaoSemEstoqueException;
import com.cernecommerce.core.domain.exception.estoque.SalaoWarehouseAlreadyExistsException;
import com.cernecommerce.core.domain.exception.estoque.SameWarehouseTransferException;
import com.cernecommerce.core.domain.exception.pdv.SessionTierNotFoundException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.OpenPackage;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.core.ports.out.estoque.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EST-F038/EST-F039/EST-F040/PDV-F048 contra banco real: o depósito do Salão, a transferência a
 * custo da loja para ele, a sessão consumindo a lata do Salão (e não a do caixa), o cadastro de lata
 * já aberta com data e baixa, e a faixa da sessão no produto.
 *
 * <p>Tudo numa transação desfeita no fim. Se o banco de dev já tiver um Salão ativo, ele é
 * desligado dentro da transação, para cada teste criar o seu.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SalaoEssenciasIT {

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired SessionMenuUseCase sessionMenuUseCase;
    @Autowired WarehouseRepository warehouseRepository;

    @PersistenceContext EntityManager em;

    private String suffix;
    private String operator;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        operator = "caixa-" + suffix;
        warehouseRepository.findActiveByType(WarehouseType.SALAO)
                .ifPresent(existing -> estoqueUseCase.setWarehouseActive(existing.code(), false));
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Essência de sessão com lata de 5 sessões e custo de cadastro R$ 40. */
    private String essencia(String nome) {
        String sku = "ESS-" + nome + "-" + suffix;
        estoqueUseCase.createProduct(sku, "Zomo " + nome + " " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 5, null));
        return sku;
    }

    private BigDecimal saldo(String sku, String warehouse) {
        return estoqueUseCase.getStockBalance(sku, warehouse).quantity();
    }

    // ── Depósito do Salão (EST-F038) ─────────────────────────────────────────────────────────

    @Test
    void criarOSalao_levaAsLatasAbertasParaEle_eUmSegundoERecusado() {
        String loja = "LOJA-" + suffix;
        String sabor = essencia("MENTA");
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.registerOpenPackage(sabor, loja, 2, null, false, operator);
        flushAndClear();

        estoqueUseCase.createWarehouse("SALAO-" + suffix, "Salão " + suffix, WarehouseType.SALAO);
        flushAndClear();

        OpenPackage lata = estoqueUseCase.findOpenPackage(sabor, "SALAO-" + suffix);
        assertThat(lata.uses()).isEqualTo(3);
        assertThat(estoqueUseCase.listOpenPackages(loja)).isEmpty();

        assertThatThrownBy(() -> estoqueUseCase.createWarehouse("SALAO2-" + suffix, "Outro", WarehouseType.SALAO))
                .isInstanceOf(SalaoWarehouseAlreadyExistsException.class);
    }

    // ── Transferência (EST-F039) ─────────────────────────────────────────────────────────────

    @Test
    void transferencia_levaOCustoMedioDaOrigem_eEntraNoResumo() {
        String loja = "LOJA-" + suffix;
        String salao = "SALAO-" + suffix;
        String sabor = essencia("UVA");
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createWarehouse(salao, "Salão " + suffix, WarehouseType.SALAO);
        estoqueUseCase.adjustStock(sabor, loja, MovementType.ENTRADA, new BigDecimal("10"), "compra", operator,
                null, null, new BigDecimal("32.00"), null);
        flushAndClear();

        EstoqueUseCase.StockTransferResult result = estoqueUseCase.transferStock(sabor, loja, salao, 3,
                "Consumo do Salão (transferência a custo)", operator);
        flushAndClear();

        assertThat(result.from().quantity()).isEqualByComparingTo("7");
        assertThat(result.to().quantity()).isEqualByComparingTo("3");
        assertThat(result.unitCost()).isEqualByComparingTo("32.00");
        assertThat(result.totalCost()).isEqualByComparingTo("96.00");
        assertThat(estoqueUseCase.getStockBalance(sabor, salao).averageCost()).isEqualByComparingTo("32.00");

        List<EstoqueUseCase.TransferSummaryLine> resumo = estoqueUseCase.transferSummary(salao, null, null);
        assertThat(resumo).singleElement().satisfies(l -> {
            assertThat(l.sku()).isEqualTo(sabor);
            assertThat(l.quantity()).isEqualByComparingTo("3");
            assertThat(l.totalCost()).isEqualByComparingTo("96.00");
        });
        // Entrada comum no Salão não é transferência e não entra no resumo.
        estoqueUseCase.adjustStock(sabor, salao, MovementType.ENTRADA, BigDecimal.ONE, "achado", operator);
        flushAndClear();
        assertThat(estoqueUseCase.transferSummary(salao, null, null)).singleElement()
                .satisfies(l -> assertThat(l.quantity()).isEqualByComparingTo("3"));
    }

    @Test
    void transferencia_semCustoMedio_usaOCustoDoCadastro() {
        String loja = "LOJA-" + suffix;
        String salao = "SALAO-" + suffix;
        String sabor = essencia("LIMAO");
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createWarehouse(salao, "Salão " + suffix, WarehouseType.SALAO);
        estoqueUseCase.adjustStock(sabor, loja, MovementType.ENTRADA, new BigDecimal("2"), "carga", operator);

        EstoqueUseCase.StockTransferResult result = estoqueUseCase.transferStock(sabor, loja, salao, 2,
                "Consumo do Salão", operator);

        assertThat(result.unitCost()).isEqualByComparingTo("40.00");
        assertThat(result.totalCost()).isEqualByComparingTo("80.00");
    }

    @Test
    void transferencia_semSaldo_naoMovimentaNada_eMesmoDepositoERecusado() {
        String loja = "LOJA-" + suffix;
        String salao = "SALAO-" + suffix;
        String sabor = essencia("MELANCIA");
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createWarehouse(salao, "Salão " + suffix, WarehouseType.SALAO);
        estoqueUseCase.adjustStock(sabor, loja, MovementType.ENTRADA, new BigDecimal("1"), "carga", operator);
        flushAndClear();

        assertThatThrownBy(() -> estoqueUseCase.transferStock(sabor, loja, salao, 2, "Consumo", operator))
                .isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> estoqueUseCase.transferStock(sabor, loja, loja, 1, "Consumo", operator))
                .isInstanceOf(SameWarehouseTransferException.class);
        flushAndClear();

        assertThat(saldo(sabor, loja)).isEqualByComparingTo("1");
        assertThat(saldo(sabor, salao)).isEqualByComparingTo("0");
    }

    // ── A sessão consome do Salão (EST-F038) ─────────────────────────────────────────────────

    @Test
    void comSalao_aSessaoAbreALataDoSalao_semTocarNaLoja_eSemLataFechadaERecusada() {
        String loja = "LOJA-" + suffix;
        String salao = "SALAO-" + suffix;
        String comLata = essencia("BLUE");
        String semLata = essencia("PESSEGO");
        sessionMenuUseCase.createAssetType("VP" + suffix, "Vaso pequeno " + suffix, 3, false);
        sessionMenuUseCase.updateSettings(new SessionSettings("VP" + suffix, null, BigDecimal.ZERO, Set.of()));
        SessionTier tradicional = sessionMenuUseCase.createTier("Tradicional " + suffix, new BigDecimal("25.00"),
                "Zomo", 1);
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.createWarehouse(salao, "Salão " + suffix, WarehouseType.SALAO);
        estoqueUseCase.adjustStock(comLata, loja, MovementType.ENTRADA, new BigDecimal("5"), "carga", operator);
        estoqueUseCase.adjustStock(semLata, loja, MovementType.ENTRADA, new BigDecimal("5"), "carga", operator);
        estoqueUseCase.transferStock(comLata, loja, salao, 1, "Consumo do Salão", operator);

        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, loja);
        Comanda mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 1", operator);
        flushAndClear();

        ComandaItem sessao = comandaUseCase.addSession(mesa.id(), new ComandaUseCase.AddSessionCommand(
                tradicional.id(), null, false, null, List.of(), false, null, null, false, comLata, null),
                operator).items().get(0);
        flushAndClear();

        assertThat(sessao.packageUses()).isEqualTo(1);
        assertThat(estoqueUseCase.findOpenPackage(comLata, salao).uses()).isEqualTo(1);
        assertThat(saldo(comLata, salao)).isEqualByComparingTo("0");
        assertThat(saldo(comLata, loja)).isEqualByComparingTo("4");
        assertThat(estoqueUseCase.listOpenPackages(loja)).isEmpty();

        assertThatThrownBy(() -> comandaUseCase.addSession(mesa.id(), new ComandaUseCase.AddSessionCommand(
                tradicional.id(), null, false, null, List.of(), false, null, null, false, semLata, null), operator))
                .isInstanceOf(SalaoSemEstoqueException.class);
        flushAndClear();
        assertThat(saldo(semLata, loja)).isEqualByComparingTo("5");

        // Remover a sessão devolve o uso à lata do Salão, onde ela foi consumida.
        comandaUseCase.removeItem(mesa.id(), sessao.id(), operator);
        flushAndClear();
        assertThat(estoqueUseCase.findOpenPackage(comLata, salao).uses()).isZero();
    }

    // ── Lata já aberta com data e baixa (EST-F040) ───────────────────────────────────────────

    @Test
    void lataJaAberta_comWithdraw_baixaUmaUnidade_eGuardaODiaDaAbertura() {
        String loja = "LOJA-" + suffix;
        String sabor = essencia("COCO");
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        estoqueUseCase.adjustStock(sabor, loja, MovementType.ENTRADA, new BigDecimal("2"), "carga", operator);
        LocalDate ontem = LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusDays(1);

        OpenPackage lata = estoqueUseCase.registerOpenPackage(sabor, loja, 4, ontem, true, operator);
        flushAndClear();

        assertThat(lata.openedAt()).isEqualTo(ontem.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant());
        assertThat(saldo(sabor, loja)).isEqualByComparingTo("1");
    }

    @Test
    void lataJaAberta_comWithdraw_semSaldo_naoCadastraNada() {
        String loja = "LOJA-" + suffix;
        String sabor = essencia("ABACAXI");
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        flushAndClear();

        assertThatThrownBy(() -> estoqueUseCase.registerOpenPackage(sabor, loja, 4, null, true, operator))
                .isInstanceOf(InsufficientStockException.class);
        flushAndClear();
        assertThat(estoqueUseCase.listOpenPackages(loja)).isEmpty();
    }

    // ── Faixa da sessão no produto (PDV-F048) ────────────────────────────────────────────────

    @Test
    void faixaDaSessao_gravaTrocaELimpa_eFaixaInexistenteERecusada() {
        String sabor = essencia("MANGA");
        SessionTier premium = sessionMenuUseCase.createTier("Premium " + suffix, new BigDecimal("30.00"), "Zomo", 2);

        estoqueUseCase.updateProduct(sabor, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(null, null, null, null, premium.id(), false));
        flushAndClear();
        assertThat(estoqueUseCase.findProductBySku(sabor).sessionTierId()).isEqualTo(premium.id());

        estoqueUseCase.updateProduct(sabor, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(null, null, null, null, null, true));
        flushAndClear();
        assertThat(estoqueUseCase.findProductBySku(sabor).sessionTierId()).isNull();

        assertThatThrownBy(() -> estoqueUseCase.updateProduct(sabor, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(null, null, null, null, -1L, false)))
                .isInstanceOf(SessionTierNotFoundException.class);
    }
}
