package com.cernecommerce.adapter.out.persistence.repository;

import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.in.CrmUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o adapter de persistência de comandas contra banco real (PDV-F009).
 *
 * <p>Mesma razão de {@code PedidoRepositoryIT}: a suíte de unidade mocka {@code ComandaRepository},
 * então nada exercitava o mapeamento domínio↔entidade de {@code comanda}/{@code comanda_item} nem a
 * query de "comandas abertas" — que PDV-C007 abriu para a loja inteira e PDV-C009 passou a carregar com
 * {@code JOIN FETCH}.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ComandaRepositoryIT {

    @Autowired ComandaRepositoryImpl comandaRepository;
    @Autowired CrmUseCase crmUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private static ComandaItem essenciaItem() {
        return ComandaItem.of(null, "ESS-MENTA", BigDecimal.ONE, new BigDecimal("25.00"),
                new BigDecimal("10.00"), "Essência Menta", Instant.now());
    }

    private static ComandaItem sessionItem(String sku, String unitPrice, ConsumptionMode mode,
            boolean courtesy, Long linkedItemId) {
        return ComandaItem.of(null, sku, BigDecimal.ONE, new BigDecimal(unitPrice),
                new BigDecimal("10.00"), "Sessão " + sku, Instant.now(), mode, courtesy, linkedItemId);
    }

    @Test
    void save_persistsAndReloadsAnOpenComanda() {
        Comanda saved = comandaRepository.save(Comanda.open(1L, "LOJA-01", "Mesa 4", "caixa1"));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.sessionId()).isEqualTo(1L);
        assertThat(reloaded.warehouseCode()).isEqualTo("LOJA-01");
        assertThat(reloaded.tableOrCustomerLabel()).isEqualTo("Mesa 4");
        assertThat(reloaded.status()).isEqualTo(ComandaStatus.ABERTA);
        assertThat(reloaded.openedBy()).isEqualTo("caixa1");
        assertThat(reloaded.items()).isEmpty();
    }

    @Test
    void save_roundTripsAccumulatedItemsWithFrozenPrices() {
        Comanda comanda = Comanda.open(2L, "LOJA-01", "Mesa 5", "caixa1")
                .withAddedItem(essenciaItem());
        Comanda saved = comandaRepository.save(comanda);
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("ESS-MENTA");
            assertThat(item.quantity()).isEqualByComparingTo("1");
            assertThat(item.unitPrice()).isEqualByComparingTo("25.00");
            assertThat(item.costPrice()).isEqualByComparingTo("10.00");
            assertThat(item.productName()).isEqualTo("Essência Menta");
            assertThat(item.addedAt()).isNotNull();
        });
        assertThat(reloaded.runningTotal()).isEqualByComparingTo("25.00");
    }

    @Test
    void findOpen_returnsOnlyAbertaComandas() {
        Comanda aberta = comandaRepository.save(Comanda.open(3L, "LOJA-01", "Mesa 1", "caixa1"));
        Comanda fechada = comandaRepository.save(Comanda.open(3L, "LOJA-01", "Mesa 2", "caixa1")
                .withAddedItem(essenciaItem()));
        comandaRepository.save(fechada.closed(999L, Instant.now()));
        Comanda cancelada = comandaRepository.save(Comanda.open(3L, "LOJA-01", "Mesa 3", "caixa1"));
        comandaRepository.save(cancelada.cancelled(Instant.now()));
        flushAndClear();

        List<Comanda> abertas = comandaRepository.findOpen(3L, null, 0, 50).content();

        assertThat(abertas).extracting(Comanda::id).containsExactly(aberta.id());
    }

    /**
     * PDV-C007, o ponto da entrega: sem {@code sessionId} a consulta devolve as mesas de
     * <b>todas</b> as sessões. Era a obrigatoriedade do filtro que forçava o cliente a listar as
     * sessões abertas e disparar uma chamada por sessão, mesclando o resultado no navegador.
     *
     * <p>Sem filtro por status da sessão de caixa, e isso é a invariante de PDV-C005: caixa não
     * fecha com mesa aberta, logo mesa {@code ABERTA} já implica sessão {@code OPEN}.</p>
     */
    @Test
    void findOpen_withoutSessionId_returnsTheMesasOfEveryCashRegisterSession() {
        Comanda daSessao30 = comandaRepository.save(Comanda.open(30L, "SALAO-A", "Mesa 1", "caixa1"));
        Comanda daSessao31 = comandaRepository.save(Comanda.open(31L, "SALAO-A", "Mesa 2", "caixa2"));
        Comanda fechada = comandaRepository.save(Comanda.open(32L, "SALAO-A", "Mesa 3", "caixa3")
                .withAddedItem(essenciaItem()));
        comandaRepository.save(fechada.closed(998L, Instant.now()));
        flushAndClear();

        List<Comanda> abertas = comandaRepository.findOpen(null, "SALAO-A", 0, 50).content();

        assertThat(abertas).extracting(Comanda::id)
                .containsExactly(daSessao31.id(), daSessao30.id())
                .doesNotContain(fechada.id());
        // Duas gavetas diferentes numa consulta só — o merge que o cliente fazia à mão.
        assertThat(abertas).extracting(Comanda::sessionId).containsExactly(31L, 30L);
    }

    /**
     * A guarda de PDV-C005 tem consulta própria, não paginada: ela precisa de <b>todas</b> as mesas
     * abertas do caixa para decidir, e uma página cortaria a resposta em silêncio — um caixa com
     * mais mesas que o tamanho da página voltaria a fechar com mesa aberta, que é o bug que aquela
     * correção fechou.
     */
    @Test
    void findOpenIdsBySessionId_returnsEveryOpenMesaOfTheSession() {
        Comanda a = comandaRepository.save(Comanda.open(45L, "SALAO-G", "Mesa 1", "caixa1"));
        Comanda b = comandaRepository.save(Comanda.open(45L, "SALAO-G", "Mesa 2", "caixa1"));
        Comanda fechada = comandaRepository.save(Comanda.open(45L, "SALAO-G", "Mesa 3", "caixa1")
                .withAddedItem(essenciaItem()));
        comandaRepository.save(fechada.closed(997L, Instant.now()));
        comandaRepository.save(Comanda.open(46L, "SALAO-G", "Mesa de outro caixa", "caixa2"));
        flushAndClear();

        assertThat(comandaRepository.findOpenIdsBySessionId(45L))
                .containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(comandaRepository.findOpenIdsBySessionId(45L)).doesNotContain(fechada.id());
    }

    @Test
    void findOpen_filtersByWarehouseCode() {
        Comanda naLoja = comandaRepository.save(Comanda.open(40L, "SALAO-B", "Mesa 1", "caixa1"));
        comandaRepository.save(Comanda.open(41L, "SALAO-C", "Mesa 2", "caixa2"));
        flushAndClear();

        assertThat(comandaRepository.findOpen(null, "SALAO-B", 0, 50).content())
                .extracting(Comanda::id).containsExactly(naLoja.id());
    }

    /**
     * PDV-C012 — a rota devolvia {@code List} sem teto. A ordem é {@code id DESC}, chave única e
     * monotônica, então a paginação é determinística: nenhuma mesa aparece em duas páginas nem some
     * entre elas (a armadilha que EST-C012 documentou no ledger de estoque).
     */
    @Test
    void findOpen_paginatesWithAStableOrder() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(comandaRepository.save(Comanda.open(50L, "SALAO-D", "Mesa " + i, "caixa1")).id());
        }
        flushAndClear();
        List<Long> esperados = new ArrayList<>(ids);
        esperados.sort(Comparator.reverseOrder());

        PageResult<Comanda> primeira = comandaRepository.findOpen(50L, null, 0, 2);
        PageResult<Comanda> segunda = comandaRepository.findOpen(50L, null, 1, 2);
        PageResult<Comanda> terceira = comandaRepository.findOpen(50L, null, 2, 2);

        assertThat(primeira.totalElements()).isEqualTo(5);
        assertThat(primeira.totalPages()).isEqualTo(3);
        assertThat(primeira.content()).extracting(Comanda::id).containsExactly(esperados.get(0), esperados.get(1));
        assertThat(segunda.content()).extracting(Comanda::id).containsExactly(esperados.get(2), esperados.get(3));
        assertThat(terceira.content()).extracting(Comanda::id).containsExactly(esperados.get(4));
    }

    /** Página além do fim devolve vazio, não estoura o {@code IN ()} da segunda consulta. */
    @Test
    void findOpen_pastTheLastPage_returnsEmptyWithoutFailing() {
        comandaRepository.save(Comanda.open(60L, "SALAO-E", "Mesa 1", "caixa1"));
        flushAndClear();

        PageResult<Comanda> vazia = comandaRepository.findOpen(60L, null, 9, 50);

        assertThat(vazia.content()).isEmpty();
        assertThat(vazia.totalElements()).isEqualTo(1);
    }

    /**
     * PDV-C009 — a prova de que o N+1 morreu: o número de consultas <b>não cresce</b> com o número
     * de mesas. Antes {@code toDomain} tocava a coleção {@code LAZY} de cada comanda, uma consulta
     * por mesa aberta; com PDV-C007 abrindo a listagem para a loja inteira isso ficaria pior, não
     * melhor.
     *
     * <p>Contado por {@code Statistics} do Hibernate porque não há outro jeito honesto de afirmar
     * isso — os itens estariam acessíveis nos dois desenhos, já que a leitura acontece dentro da
     * transação. É o primeiro teste de contagem de consultas do módulo.</p>
     */
    @Test
    void findOpen_loadsItemsWithoutOneQueryPerComanda() {
        Statistics stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        // O contexto do Spring é compartilhado com o resto da suíte: liga a estatística, mede, e
        // devolve o estado como estava no finally.
        boolean estavaLigada = stats.isStatisticsEnabled();
        stats.setStatisticsEnabled(true);
        try {
            comandaRepository.save(Comanda.open(70L, "SALAO-F", "Mesa 1", "caixa1")
                    .withAddedItem(essenciaItem()));
            flushAndClear();
            stats.clear();
            List<Comanda> umaMesa = comandaRepository.findOpen(70L, null, 0, 50).content();
            long consultasComUmaMesa = stats.getPrepareStatementCount();

            for (int i = 2; i <= 4; i++) {
                comandaRepository.save(Comanda.open(70L, "SALAO-F", "Mesa " + i, "caixa1")
                        .withAddedItem(essenciaItem()));
            }
            flushAndClear();
            stats.clear();
            List<Comanda> quatroMesas = comandaRepository.findOpen(70L, null, 0, 50).content();
            long consultasComQuatroMesas = stats.getPrepareStatementCount();

            // Os itens vieram junto, nas duas leituras.
            assertThat(umaMesa).hasSize(1);
            assertThat(umaMesa.getFirst().items()).hasSize(1);
            assertThat(quatroMesas).hasSize(4);
            assertThat(quatroMesas).allSatisfy(c -> assertThat(c.items()).hasSize(1));

            // E quadruplicar as mesas não mudou o número de consultas: ids + fetch, sempre.
            assertThat(consultasComQuatroMesas).isEqualTo(consultasComUmaMesa);
        } finally {
            stats.setStatisticsEnabled(estavaLigada);
        }
    }

    @Test
    void save_transitionsToFechadaAndPersistsOrderId() {
        Comanda comanda = comandaRepository.save(Comanda.open(4L, "LOJA-01", "Mesa 6", "caixa1")
                .withAddedItem(essenciaItem()));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();

        Comanda fechada = comandaRepository.save(comanda.closed(777L, Instant.now()));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(fechada.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(reloaded.orderId()).isEqualTo(777L);
        assertThat(reloaded.closedAt()).isNotNull();
    }

    @Test
    void save_transitionsToCanceladaAndPersistsClosedAtWithoutOrderId() {
        Comanda comanda = comandaRepository.save(Comanda.open(5L, "LOJA-01", "Mesa 7", "caixa1")
                .withAddedItem(essenciaItem()));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();

        Comanda cancelada = comandaRepository.save(comanda.cancelled(Instant.now()));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(cancelada.id()).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(ComandaStatus.CANCELADA);
        assertThat(reloaded.orderId()).isNull();
        assertThat(reloaded.closedAt()).isNotNull();
        // Itens permanecem: é o rastro de que a comanda existiu, mesmo abandonada.
        assertThat(reloaded.items()).hasSize(1);
    }

    @Test
    void save_roundTripsSessionFieldsAndCustomer() {
        // customer_id tem FK para customers(id) — não dá para inventar um número aqui.
        Customer cliente = crmUseCase.createCustomer("Cliente Mesa", "11999990000", null, null, "mesa");

        Comanda comanda = Comanda.open(6L, "LOJA-01", "Mesa 8", cliente.id(), "caixa1")
                .withAddedItem(sessionItem("SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null));
        Comanda saved = comandaRepository.save(comanda);
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(reloaded.customerId()).isEqualTo(cliente.id());
        assertThat(reloaded.items()).singleElement().satisfies(item -> {
            assertThat(item.mode()).isEqualTo(ConsumptionMode.OPEN_ROSH);
            assertThat(item.courtesy()).isFalse();
            assertThat(item.linkedItemId()).isNull();
            // Custo congelado mesmo com o preço vindo de fora do SKU — é ele que faz a margem
            // mostrar o prejuízo real do open rosh.
            assertThat(item.costPrice()).isEqualByComparingTo("10.00");
        });
    }

    @Test
    void save_roundTripsCourtesyLineWithFrozenCost() {
        Comanda comanda = comandaRepository.save(Comanda.open(7L, "LOJA-01", "Mesa 10", "caixa1")
                .withAddedItem(sessionItem("SESS-MENTA", "25.00", ConsumptionMode.NORMAL, false, null)));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();
        Long sessaoId = comanda.items().get(0).id();

        Comanda saved = comandaRepository.save(comanda.withAddedItem(
                sessionItem("SESS-UVA", "0.00", ConsumptionMode.SABOR_EXTRA, true, sessaoId)));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.items()).hasSize(2);
        assertThat(reloaded.items().get(1)).satisfies(extra -> {
            assertThat(extra.mode()).isEqualTo(ConsumptionMode.SABOR_EXTRA);
            assertThat(extra.courtesy()).isTrue();
            assertThat(extra.unitPrice()).isEqualByComparingTo("0.00");
            // Cortesia não é linha grátis para a contabilidade: o custo continua congelado.
            assertThat(extra.costPrice()).isEqualByComparingTo("10.00");
            assertThat(extra.linkedItemId()).isEqualTo(sessaoId);
        });
        // Só a linha cobrada entra no total.
        assertThat(reloaded.runningTotal()).isEqualByComparingTo("25.00");
    }

    /**
     * Regressão do apaga-e-reinsere: {@code save} reescrevia a lista inteira de itens a cada
     * lançamento, dando id novo a cada linha. Desde a V114 isso quebra de duas formas — a FK
     * {@code linked_item_id → comanda_item(id)} passa a apontar para uma linha recém-deletada, e o
     * id que o cliente recebeu para mandar de volta no {@code TROCA} morre no lançamento seguinte.
     */
    @Test
    void save_keepsItemIdsStableAcrossLaunches_soLinkedItemIdSurvives() {
        Comanda comanda = comandaRepository.save(Comanda.open(8L, "LOJA-01", "Mesa 11", "caixa1")
                .withAddedItem(sessionItem("SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null)));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();
        Long openRoshId = comanda.items().get(0).id();

        // Troca pendurada no open rosh — é aqui que a FK estourava.
        comanda = comandaRepository.save(comanda.withAddedItem(
                sessionItem("SESS-UVA", "0.00", ConsumptionMode.TROCA, true, openRoshId)));
        flushAndClear();
        comanda = comandaRepository.findById(comanda.id()).orElseThrow();
        assertThat(comanda.items().get(0).id()).isEqualTo(openRoshId);

        // E um terceiro lançamento qualquer não pode mexer nos ids das duas linhas anteriores,
        // ou o cliente ficaria com um linkedItemId morto na mão.
        Long trocaId = comanda.items().get(1).id();
        Comanda saved = comandaRepository.save(comanda.withAddedItem(essenciaItem()));
        flushAndClear();

        Comanda reloaded = comandaRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.items()).extracting(ComandaItem::id)
                .containsExactly(openRoshId, trocaId, reloaded.items().get(2).id());
        assertThat(reloaded.items().get(1).linkedItemId()).isEqualTo(openRoshId);
    }

    /**
     * PDV-F011 — ida e volta de {@code notes} e {@code surcharge_amount}. Mapeamento novo em coluna
     * nova: sem este teste, um {@code @Column} com o nome errado só apareceria em produção, com a
     * nota do setup sumindo em silêncio no reload.
     */
    @Test
    void save_roundTripsNotesAndSurchargeAmount() {
        String setup = "Narguilé grande · Com filtro · Pinça P-02";
        ComandaItem comAcrescimo = ComandaItem.of(null, "SESS-BLUE", BigDecimal.ONE,
                new BigDecimal("75.00"), new BigDecimal("10.00"), "Sessão Blueberry", Instant.now(),
                ConsumptionMode.OPEN_ROSH, false, null, setup, new BigDecimal("15.00"));

        Comanda saved = comandaRepository.save(
                Comanda.open(20L, "LOJA-01", "Mesa 15", "caixa1").withAddedItem(comAcrescimo));
        flushAndClear();

        ComandaItem reloaded = comandaRepository.findById(saved.id()).orElseThrow().items().get(0);
        assertThat(reloaded.notes()).isEqualTo(setup);
        assertThat(reloaded.surchargeAmount()).isEqualByComparingTo("15.00");
        // O unitPrice gravado é o total já somado — é ele que faz o subtotal fechar.
        assertThat(reloaded.unitPrice()).isEqualByComparingTo("75.00");
        assertThat(reloaded.costPrice()).isEqualByComparingTo("10.00");
    }

    /** Linha sem os dois campos continua nula depois do reload — null é "não teve", não zero. */
    @Test
    void save_leavesNotesAndSurchargeNullWhenTheLineHasNeither() {
        Comanda saved = comandaRepository.save(
                Comanda.open(21L, "LOJA-01", "Mesa 16", "caixa1").withAddedItem(essenciaItem()));
        flushAndClear();

        ComandaItem reloaded = comandaRepository.findById(saved.id()).orElseThrow().items().get(0);
        assertThat(reloaded.notes()).isNull();
        assertThat(reloaded.surchargeAmount()).isNull();
    }

    /**
     * PDV-C008 — a leitura travada devolve a mesma comanda que a leitura comum. O que a trava faz
     * (segurar a linha até o fim da transação) só se prova sob concorrência real, em
     * {@code ComandaConcurrencyIT}; aqui o que se garante é que o {@code @Query} novo não mudou o
     * resultado nem quebrou o mapeamento.
     */
    @Test
    void findByIdForUpdate_returnsTheSameAggregateAsFindById() {
        Comanda saved = comandaRepository.save(
                Comanda.open(22L, "LOJA-01", "Mesa 17", "caixa1").withAddedItem(essenciaItem()));
        flushAndClear();

        Comanda travada = comandaRepository.findByIdForUpdate(saved.id()).orElseThrow();
        Comanda comum = comandaRepository.findById(saved.id()).orElseThrow();

        assertThat(travada.id()).isEqualTo(comum.id());
        assertThat(travada.status()).isEqualTo(comum.status());
        assertThat(travada.items()).hasSameSizeAs(comum.items());
        assertThat(travada.runningTotal()).isEqualByComparingTo(comum.runningTotal());
    }

    @Test
    void findByIdForUpdate_returnsEmptyForAnUnknownId() {
        assertThat(comandaRepository.findByIdForUpdate(999_999L)).isEmpty();
    }

    // ── Varredura de mesa esquecida (PDV-F013) ───────────────────────────────────────────────

    /**
     * A consulta que a varredura usa. Exercitada aqui e não só no service porque o filtro é por
     * {@code openedAt}, coluna que nenhuma outra query deste repositório toca — um erro de
     * comparação passaria batido na suíte de unidade, que mocka o repositório inteiro.
     */
    @Test
    void findOpenIdsOlderThan_returnsOnlyOpenComandasOpenedBeforeTheCutoff() {
        Comanda velha = comandaRepository.save(comandaAbertaEm(Instant.now().minus(13, ChronoUnit.HOURS)));
        Comanda recente = comandaRepository.save(comandaAbertaEm(Instant.now().minus(1, ChronoUnit.HOURS)));
        flushAndClear();

        List<Long> ids = comandaRepository.findOpenIdsOlderThan(Instant.now().minus(12, ChronoUnit.HOURS), 100);

        assertThat(ids).contains(velha.id());
        assertThat(ids).doesNotContain(recente.id());
    }

    /** Mesa velha porém já resolvida não interessa: a varredura só age sobre o que segue ABERTO. */
    @Test
    void findOpenIdsOlderThan_ignoresClosedAndCancelledComandas() {
        Comanda cancelada = comandaRepository.save(
                comandaAbertaEm(Instant.now().minus(20, ChronoUnit.HOURS)).cancelled(Instant.now()));
        flushAndClear();

        List<Long> ids = comandaRepository.findOpenIdsOlderThan(Instant.now().minus(12, ChronoUnit.HOURS), 100);

        assertThat(ids).doesNotContain(cancelada.id());
    }

    /** O teto do lote é respeitado — a varredura processa uma passada por vez. */
    @Test
    void findOpenIdsOlderThan_honoursTheBatchLimit() {
        comandaRepository.save(comandaAbertaEm(Instant.now().minus(30, ChronoUnit.HOURS)));
        comandaRepository.save(comandaAbertaEm(Instant.now().minus(29, ChronoUnit.HOURS)));
        comandaRepository.save(comandaAbertaEm(Instant.now().minus(28, ChronoUnit.HOURS)));
        flushAndClear();

        List<Long> ids = comandaRepository.findOpenIdsOlderThan(Instant.now().minus(12, ChronoUnit.HOURS), 2);

        assertThat(ids).hasSize(2);
    }

    private static Comanda comandaAbertaEm(Instant openedAt) {
        return Comanda.of(null, 90L, "LOJA-01", "Mesa esquecida", null, ComandaStatus.ABERTA,
                List.of(), null, "caixa1", openedAt, null);
    }

    // ── Conta dividida e junção de mesas (PDV-F017 / PDV-F016) ───────────────────────────────

    @Test
    void save_roundTripsClosedInOrderId() {
        Comanda comanda = comandaRepository.save(
                Comanda.open(80L, "LOJA-01", "Mesa dividida", "caixa1").withAddedItem(essenciaItem()));
        flushAndClear();

        Comanda carregada = comandaRepository.findById(comanda.id()).orElseThrow();
        Long itemId = carregada.items().get(0).id();
        comandaRepository.save(carregada.withItemsClosedIn(777L, List.of(itemId)));
        flushAndClear();

        Comanda relida = comandaRepository.findById(comanda.id()).orElseThrow();
        assertThat(relida.items().get(0).closedInOrderId()).isEqualTo(777L);
        assertThat(relida.openItems()).isEmpty();
        assertThat(relida.runningTotal()).isEqualByComparingTo("0.00");
    }

    /**
     * <b>O teste que justifica o desenho de PDV-F016.</b> Mover as linhas por reatribuição de FK
     * preserva os ids — e é isso que mantém {@code linkedItemId} apontando para a linha certa. Passar
     * os itens pelo {@code save} da comanda destino criaria ids novos e a TROCA ficaria órfã.
     */
    @Test
    void moveItems_preservesIdsAndKeepsLinkedItemIdValid() {
        Comanda origem = comandaRepository.save(
                Comanda.open(81L, "LOJA-01", "Mesa A", "caixa1")
                        .withAddedItem(sessionItem("SESS-BLUE", "60.00", ConsumptionMode.OPEN_ROSH, false, null)));
        flushAndClear();
        Comanda comSessao = comandaRepository.findById(origem.id()).orElseThrow();
        Long sessaoId = comSessao.items().get(0).id();
        comandaRepository.save(comSessao.withAddedItem(
                sessionItem("ESS-UVA", "0.00", ConsumptionMode.TROCA, true, sessaoId)));
        flushAndClear();

        Comanda destino = comandaRepository.save(Comanda.open(81L, "LOJA-01", "Mesa B", "caixa1"));
        flushAndClear();

        List<Long> ids = comandaRepository.findById(origem.id()).orElseThrow().items().stream()
                .map(ComandaItem::id).toList();
        int movidos = comandaRepository.moveItems(origem.id(), destino.id(), ids);
        flushAndClear();

        assertThat(movidos).isEqualTo(2);
        Comanda destinoRelido = comandaRepository.findById(destino.id()).orElseThrow();
        assertThat(destinoRelido.items()).hasSize(2);
        // Os ids sobreviveram à mudança de comanda...
        assertThat(destinoRelido.items()).extracting(ComandaItem::id).contains(sessaoId);
        // ...e por isso a TROCA continua apontando para a sessão dela.
        ComandaItem troca = destinoRelido.items().stream()
                .filter(i -> i.mode() == ConsumptionMode.TROCA).findFirst().orElseThrow();
        assertThat(troca.linkedItemId()).isEqualTo(sessaoId);
        assertThat(comandaRepository.findById(origem.id()).orElseThrow().items()).isEmpty();
    }

    /** Id de outra comanda não é arrastado, mesmo que chegue na lista por engano. */
    @Test
    void moveItems_onlyMovesLinesOfTheSourceComanda() {
        Comanda origem = comandaRepository.save(
                Comanda.open(82L, "LOJA-01", "Mesa A", "caixa1").withAddedItem(essenciaItem()));
        Comanda outra = comandaRepository.save(
                Comanda.open(82L, "LOJA-01", "Mesa C", "caixa1").withAddedItem(essenciaItem()));
        Comanda destino = comandaRepository.save(Comanda.open(82L, "LOJA-01", "Mesa B", "caixa1"));
        flushAndClear();
        Long daOutra = comandaRepository.findById(outra.id()).orElseThrow().items().get(0).id();

        int movidos = comandaRepository.moveItems(origem.id(), destino.id(), List.of(daOutra));
        flushAndClear();

        assertThat(movidos).isZero();
        assertThat(comandaRepository.findById(outra.id()).orElseThrow().items()).hasSize(1);
        assertThat(comandaRepository.findById(destino.id()).orElseThrow().items()).isEmpty();
    }
}
