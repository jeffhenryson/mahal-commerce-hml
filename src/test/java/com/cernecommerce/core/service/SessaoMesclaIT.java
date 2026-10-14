package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.pdv.SameFlavorMixException;
import com.cernecommerce.core.domain.exception.pdv.SessionTierRequiredException;
import com.cernecommerce.core.domain.model.estoque.MovementType;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PDV-F050/PDV-F051 contra banco real: a faixa da sessão sai da ficha da essência (a maior, na
 * mescla), e a sessão mesclada grava os dois sabores, consome 1 uso da lata de cada um e devolve os
 * dois ao ser removida.
 *
 * <p>Tudo numa transação desfeita no fim. Um Salão ativo no banco de dev é desligado dentro dela,
 * para as latas serem as do caixa do teste.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SessaoMesclaIT {

    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired SessionMenuUseCase sessionMenuUseCase;
    @Autowired WarehouseRepository warehouseRepository;

    @PersistenceContext EntityManager em;

    private String suffix;
    private String operator;
    private String loja;
    private SessionTier tradicional;
    private SessionTier premium;
    private Comanda mesa;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        operator = "caixa-" + suffix;
        loja = "LOJA-" + suffix;
        warehouseRepository.findActiveByType(WarehouseType.SALAO)
                .ifPresent(existing -> estoqueUseCase.setWarehouseActive(existing.code(), false));
        sessionMenuUseCase.createAssetType("VP" + suffix, "Vaso pequeno " + suffix, 5, false);
        sessionMenuUseCase.updateSettings(new SessionSettings("VP" + suffix, null, BigDecimal.ZERO, Set.of()));
        tradicional = sessionMenuUseCase.createTier("Tradicional " + suffix, new BigDecimal("25.00"), "", 1);
        premium = sessionMenuUseCase.createTier("Premium " + suffix, new BigDecimal("30.00"), "", 2);
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, loja);
        mesa = comandaUseCase.openComanda(caixa.id(), "Mesa 1", operator);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /** Essência de sessão com lata de 5 sessões, a faixa informada e 5 latas fechadas na loja. */
    private String essencia(String nome, SessionTier faixa) {
        String sku = "ESS-" + nome + "-" + suffix;
        estoqueUseCase.createProduct(sku, "Zigy " + nome + " " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        estoqueUseCase.updateProduct(sku, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                new EstoqueUseCase.TableSessionCommand(true, true, 5, null, faixa == null ? null : faixa.id(),
                        false));
        estoqueUseCase.adjustStock(sku, loja, MovementType.ENTRADA, new BigDecimal("5"), "carga", operator);
        return sku;
    }

    private ComandaUseCase.AddSessionCommand sessao(Long tierId, String sku, String sku2) {
        return new ComandaUseCase.AddSessionCommand(tierId, null, false, null, List.of(), false, null, null,
                false, sku, null, sku2, null);
    }

    @Test
    void faixaDaFicha_venceOTierIdDoPedido_eSemFaixaNenhumaERecusada() {
        String comFaixa = essencia("BANANA", premium);
        String semFaixa = essencia("COCO", null);
        flushAndClear();

        ComandaItem linha = comandaUseCase.addSession(mesa.id(), sessao(tradicional.id(), comFaixa, null), operator)
                .items().get(0);
        assertThat(linha.sku()).isEqualTo(premium.sku());
        assertThat(linha.unitPrice()).isEqualByComparingTo("30.00");

        // Sem faixa na ficha, vale a do pedido; sem as duas, não há preço.
        ComandaItem pelaDoPedido = comandaUseCase.addSession(mesa.id(), sessao(tradicional.id(), semFaixa, null),
                operator).items().get(1);
        assertThat(pelaDoPedido.sku()).isEqualTo(tradicional.sku());
        assertThatThrownBy(() -> comandaUseCase.addSession(mesa.id(), sessao(null, semFaixa, null), operator))
                .isInstanceOf(SessionTierRequiredException.class);
    }

    @Test
    void mescla_cobraAMaiorFaixa_consomeUmUsoDeCadaLata_eRemoverDevolveOsDois() {
        String banana = essencia("BANANA", tradicional);
        String menta = essencia("MENTA", premium);
        flushAndClear();

        ComandaItem linha = comandaUseCase.addSession(mesa.id(), sessao(null, banana, menta), operator)
                .items().get(0);
        flushAndClear();

        assertThat(linha.sku()).isEqualTo(premium.sku());
        assertThat(linha.essenceSku()).isEqualTo(banana);
        assertThat(linha.essenceSku2()).isEqualTo(menta);
        assertThat(comandaUseCase.getComanda(mesa.id()).items().get(0).essenceSku2()).isEqualTo(menta);
        assertThat(estoqueUseCase.findOpenPackage(banana, loja).uses()).isEqualTo(1);
        assertThat(estoqueUseCase.findOpenPackage(menta, loja).uses()).isEqualTo(1);
        assertThat(estoqueUseCase.getStockBalance(banana, loja).quantity()).isEqualByComparingTo("4");
        assertThat(estoqueUseCase.getStockBalance(menta, loja).quantity()).isEqualByComparingTo("4");

        comandaUseCase.removeItem(mesa.id(), linha.id(), operator);
        flushAndClear();

        assertThat(estoqueUseCase.findOpenPackage(banana, loja).uses()).isZero();
        assertThat(estoqueUseCase.findOpenPackage(menta, loja).uses()).isZero();
    }

    @Test
    void mesclaComOMesmoSabor_eRecusada() {
        String banana = essencia("BANANA", tradicional);
        flushAndClear();

        assertThatThrownBy(() -> comandaUseCase.addSession(mesa.id(), sessao(null, banana, banana), operator))
                .isInstanceOf(SameFlavorMixException.class);
    }

    /** O 2º rosh do duplo, lançado sem sabor, recebe a mescla pelo PUT …/rosh-sabor. */
    @Test
    void segundoRoshDoDuplo_recebeAMescla() {
        String banana = essencia("BANANA", tradicional);
        String menta = essencia("MENTA", premium);
        String uva = essencia("UVA", tradicional);
        flushAndClear();

        Comanda comDuplo = comandaUseCase.addSession(mesa.id(), new ComandaUseCase.AddSessionCommand(null, null,
                false, null, List.of(), true, null, null, false, uva, null), operator);
        ComandaItem rosh = comDuplo.items().stream().filter(ComandaItem::isEssencePending).findFirst().orElseThrow();
        flushAndClear();

        ComandaItem comSabor = comandaUseCase.setRoshFlavor(mesa.id(), rosh.id(), null, banana, menta, null, operator)
                .items().stream().filter(i -> rosh.id().equals(i.id())).findFirst().orElseThrow();
        flushAndClear();

        assertThat(comSabor.essenceSku()).isEqualTo(banana);
        assertThat(comSabor.essenceSku2()).isEqualTo(menta);
        assertThat(comSabor.sku()).isEqualTo(premium.sku());
        assertThat(comSabor.unitPrice()).isEqualByComparingTo("0");
        assertThat(estoqueUseCase.findOpenPackage(menta, loja).uses()).isEqualTo(1);
    }
}
