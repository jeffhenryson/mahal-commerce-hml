package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.estoque.MovementType;
import com.cernecommerce.core.domain.model.estoque.Pricing;
import com.cernecommerce.core.domain.model.estoque.Product;
import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.salao.EscolhasSessao;
import com.cernecommerce.core.domain.model.salao.MesaOcupacao;
import com.cernecommerce.core.domain.model.salao.SalaoMesa;
import com.cernecommerce.core.domain.model.salao.SolicitacaoSessao;
import com.cernecommerce.core.domain.model.salao.SolicitacaoStatus;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.out.salao.SalaoAutoatendimentoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-C056..C062 contra banco real: as leituras em lote do QR do Salão — a última ocupação de cada
 * mesa, os ids das comandas abertas, as expiradas filtradas na consulta, o acesso do aparelho numa
 * leitura, o saldo livre só dos SKUs pedidos e os produtos por SKU de variação.
 *
 * <p>Tudo numa transação desfeita no fim.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SalaoOcupacoesIT {

    @Autowired SalaoAutoatendimentoRepository repository;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;
    @Autowired CrmUseCase crmUseCase;

    @PersistenceContext EntityManager em;

    private String suffix;
    private String operator;
    private String loja;

    @BeforeEach
    void setUp() {
        suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        operator = "caixa-" + suffix;
        loja = "LOJA-" + suffix;
        estoqueUseCase.createWarehouse(loja, "Loja " + suffix, WarehouseType.LOJA_FISICA);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private Customer cliente() {
        return crmUseCase.createCustomer("Cliente " + suffix, "1199" + suffix, null,
                String.valueOf(10000000000L + (System.nanoTime() % 89999999999L)), "QR Salão");
    }

    private SalaoMesa mesa(String nome) {
        return repository.saveMesa(new SalaoMesa(null, nome + " " + suffix, "tok-" + nome + "-" + suffix, true,
                Instant.now()));
    }

    @Test
    void ultimaOcupacaoDeCadaMesa_eComandasAbertas_emUmaConsultaCada() {
        Customer cliente = cliente();
        SalaoMesa m1 = mesa("M1");
        SalaoMesa m2 = mesa("M2");
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, loja);
        Comanda antiga = comandaUseCase.openComanda(caixa.id(), "M1 antiga", cliente.id(), operator);
        Comanda atual = comandaUseCase.openComanda(caixa.id(), "M1 atual", cliente.id(), operator);
        Comanda daM2 = comandaUseCase.openComanda(caixa.id(), "M2", cliente.id(), operator);
        Instant agora = Instant.now();
        repository.saveOcupacao(MesaOcupacao.nova(m1.id(), antiga.id(), cliente.id(), agora.minusSeconds(3600)));
        MesaOcupacao ultimaM1 = repository.saveOcupacao(MesaOcupacao.nova(m1.id(), atual.id(), cliente.id(), agora));
        MesaOcupacao ultimaM2 = repository.saveOcupacao(MesaOcupacao.nova(m2.id(), daM2.id(), cliente.id(), agora));
        comandaUseCase.cancelComanda(daM2.id(), operator, "teste");
        flushAndClear();

        List<MesaOcupacao> ultimas = repository.findUltimaOcupacaoDeCadaMesa().stream()
                .filter(o -> o.mesaId().equals(m1.id()) || o.mesaId().equals(m2.id())).toList();

        assertThat(ultimas).extracting(MesaOcupacao::id).containsExactlyInAnyOrder(ultimaM1.id(), ultimaM2.id());
        assertThat(comandaUseCase.findOpenComandaIds(List.of(antiga.id(), atual.id(), daM2.id(), -1L)))
                .containsExactlyInAnyOrder(antiga.id(), atual.id());
    }

    @Test
    void expiradas_saoAsPendentesAntesDaValidade_naPropriaConsulta() {
        Customer cliente = cliente();
        SalaoMesa m = mesa("EXP");
        Instant agora = Instant.now();
        EscolhasSessao escolhas = new EscolhasSessao(false, null, List.of(), false);
        SolicitacaoSessao vencida = repository.saveSolicitacao(SolicitacaoSessao.nova(m.id(), cliente.id(), null,
                "ZOMO-" + suffix, null, null, escolhas, "ap-1", agora.minus(Duration.ofMinutes(45))));
        SolicitacaoSessao valendo = repository.saveSolicitacao(SolicitacaoSessao.nova(m.id(), cliente.id(), null,
                "ZOMO-" + suffix, null, null, escolhas, "ap-2", agora.minusSeconds(60)));
        flushAndClear();

        List<Long> expiradas = repository.findSolicitacoesAntesDe(SolicitacaoStatus.PENDENTE,
                        agora.minus(SolicitacaoSessao.VALIDADE), 0, 500).content().stream()
                .map(SolicitacaoSessao::id).toList();

        assertThat(expiradas).contains(vencida.id()).doesNotContain(valendo.id());
        assertThat(repository.findSolicitacoesByIds(List.of(vencida.id(), valendo.id())))
                .extracting(SolicitacaoSessao::id).containsExactlyInAnyOrder(vencida.id(), valendo.id());
    }

    @Test
    void acessoDoAparelho_numaLeitura_dizSeEAcompanhante() {
        Customer cliente = cliente();
        SalaoMesa m = mesa("ACS");
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, loja);
        Comanda comanda = comandaUseCase.openComanda(caixa.id(), "ACS", cliente.id(), operator);
        MesaOcupacao o = repository.saveOcupacao(MesaOcupacao.nova(m.id(), comanda.id(), cliente.id(), Instant.now()));
        // INSERT simples: o upsert de addAcesso (ON CONFLICT ... DO UPDATE) é do Postgres, e o perfil dev é H2.
        em.flush();
        for (Object[] acesso : List.of(new Object[] {"titular", false}, new Object[] {"amigo", true})) {
            em.createNativeQuery("INSERT INTO salao_mesa_acesso (ocupacao_id, device_token, acompanhante) "
                            + "VALUES (?1, ?2, ?3)")
                    .setParameter(1, o.id()).setParameter(2, acesso[0]).setParameter(3, acesso[1])
                    .executeUpdate();
        }
        flushAndClear();

        assertThat(repository.findAcesso(o.id(), "titular")).contains(false);
        assertThat(repository.findAcesso(o.id(), "amigo")).contains(true);
        assertThat(repository.findAcesso(o.id(), "estranho")).isEqualTo(Optional.empty());
    }

    @Test
    void saldoLivre_soDosSkusPedidos_eAReservaNaoConta() {
        String comSaldo = "ESS-A-" + suffix;
        String semSaldo = "ESS-B-" + suffix;
        String foraDaLista = "ESS-C-" + suffix;
        for (String sku : List.of(comSaldo, semSaldo, foraDaLista)) {
            estoqueUseCase.createProduct(sku, "Zomo " + sku, "Essências", List.of(),
                    Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        }
        estoqueUseCase.adjustStock(comSaldo, loja, MovementType.ENTRADA, new BigDecimal("2"), "carga", operator);
        estoqueUseCase.adjustStock(foraDaLista, loja, MovementType.ENTRADA, new BigDecimal("2"), "carga", operator);
        flushAndClear();

        assertThat(estoqueUseCase.listSkusWithFreeStock(loja, Set.of(comSaldo, semSaldo)))
                .containsExactly(comSaldo);
    }

    @Test
    void produtosPorSku_doPaiEDaVariacao_cadaUmUmaVez() {
        String pai = "ESS-P-" + suffix;
        estoqueUseCase.createProduct(pai, "Zomo " + suffix, "Essências", List.of(),
                Pricing.of(new BigDecimal("40.00"), null, new BigDecimal("70.00")));
        flushAndClear();

        List<Product> achados = estoqueUseCase.findProductsByAnySku(Set.of(pai, "NAO-EXISTE-" + suffix));

        assertThat(achados).extracting(Product::sku).containsExactly(pai);
    }
}
