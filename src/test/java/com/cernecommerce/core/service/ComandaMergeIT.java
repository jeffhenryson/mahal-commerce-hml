package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.estoque.WarehouseType;
import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;
import com.cernecommerce.core.domain.model.pdv.CashRegisterSession;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionMenu;
import com.cernecommerce.core.domain.model.pdv.SessionSettings;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pdv.SessionTier;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.EstoqueUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase;
import com.cernecommerce.core.ports.in.PdvUseCase.PaymentCommand;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Juntar mesas de ponta a ponta, com as flags de PRODUÇÃO (mesa só de cardápio, sem sessão legada).
 *
 * <p>PDV-C022: até aqui nenhum teste rodava o merge inteiro contra banco — o de unidade mocka o
 * repositório e o IT do repositório não salva a origem depois do move. Foi por esse buraco que a
 * junção passou a deixar cópias das linhas na mesa encerrada. PDV-F031: a mesa juntada tem sessão
 * já paga ainda no salão, que é o caso comum desde que a sessão é paga no lançamento.</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
// Contexto próprio (flags de produção) com H2 próprio: no banco "demo" compartilhado, o create-drop
// deste contexto derrubaria o schema dos contextos em cache das outras ITs.
@TestPropertySource(properties = {"pdv.mesa.catalog-items-enabled=false", "pdv.sessao.legacy-enabled=false",
        "spring.datasource.url=jdbc:h2:mem:comanda-prod-flags;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"})
@Transactional
class ComandaMergeIT {

    @Autowired SessionMenuUseCase sessionMenuUseCase;
    @Autowired ComandaUseCase comandaUseCase;
    @Autowired PdvUseCase pdvUseCase;
    @Autowired EstoqueUseCase estoqueUseCase;

    @PersistenceContext EntityManager em;

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private long linhasNoBanco(Long comandaId) {
        return ((Number) em.createNativeQuery("SELECT COUNT(*) FROM comanda_item WHERE comanda_id = ?1")
                .setParameter(1, comandaId).getSingleResult()).longValue();
    }

    private static int disponivel(SessionMenu menu, String codigo) {
        return menu.utensilios().stream().filter(a -> a.tipo().codigo().equals(codigo)).findFirst()
                .orElseThrow().disponivel();
    }

    @Test
    void mergeOfATableWithAPaidSessionStillOnIt_movesWithoutCopiesAndClosesTheSourceOnItsOrder() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String vasoP = "VP" + suffix;
        String operator = "caixa-" + suffix;
        String warehouse = "LOUNGE-" + suffix;
        sessionMenuUseCase.createAssetType(vasoP, "Vaso pequeno " + suffix, 2, false);
        sessionMenuUseCase.createAssetType("VG" + suffix, "Vaso grande " + suffix, 1, false);
        sessionMenuUseCase.updateSettings(new SessionSettings(vasoP, "VG" + suffix, new BigDecimal("10.00"), Set.of()));
        SessionTier premium = sessionMenuUseCase.createTier("Premium " + suffix, new BigDecimal("30.00"), "Luk", 1);

        estoqueUseCase.createWarehouse(warehouse, "Lounge " + suffix, WarehouseType.LOJA_FISICA);
        CashRegisterSession caixa = pdvUseCase.openSession(operator, BigDecimal.ZERO, warehouse);
        Comanda mesa3 = comandaUseCase.openComanda(caixa.id(), "Mesa 3", operator);
        Comanda mesa5 = comandaUseCase.openComanda(caixa.id(), "Mesa 5", operator);

        // Mesa 3: uma sessão paga (vai ao preparo) e outra ainda aguardando pagamento.
        ComandaItem paga = comandaUseCase.addSession(mesa3.id(), premium.id(), "Luk Uva", false, operator)
                .items().get(0);
        Order pedido = comandaUseCase.closeComanda(mesa3.id(),
                List.of(new PaymentCommand(PaymentMethod.PIX, new BigDecimal("30.00"), null)),
                null, false, List.of(paga.id()), operator);
        Comanda comDuas = comandaUseCase.addSession(mesa3.id(), premium.id(), "Luk Menta", false, operator);
        ComandaItem naoPaga = comDuas.items().stream().filter(i -> !i.id().equals(paga.id())).findFirst().orElseThrow();
        flushAndClear();
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isZero();

        Comanda destino = comandaUseCase.mergeComanda(mesa3.id(), mesa5.id(), operator);
        flushAndClear();

        // PDV-C022 — nenhuma cópia: as duas linhas existem uma vez só, no destino, com os mesmos ids.
        assertThat(linhasNoBanco(mesa3.id())).isZero();
        assertThat(linhasNoBanco(mesa5.id())).isEqualTo(2);
        assertThat(destino.items()).extracting(ComandaItem::id).containsExactlyInAnyOrder(paga.id(), naoPaga.id());

        // PDV-F031 — a origem fecha no pedido que recebeu (receita real), o destino segue aberto.
        Comanda origem = comandaUseCase.getComanda(mesa3.id());
        assertThat(origem.status()).isEqualTo(ComandaStatus.FECHADA);
        assertThat(origem.orderId()).isEqualTo(pedido.id());
        assertThat(comandaUseCase.getHistoryEntry(mesa3.id()).orders()).extracting(Order::id)
                .containsExactly(pedido.id());

        // Os utensílios vieram junto: recolher a sessão paga no destino devolve o vaso dela.
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isZero();
        comandaUseCase.updateSessionStatus(mesa5.id(), paga.id(), SessionStatus.RECOLHIDO, operator);
        flushAndClear();
        assertThat(disponivel(sessionMenuUseCase.getMenu(), vasoP)).isEqualTo(1);
    }
}
