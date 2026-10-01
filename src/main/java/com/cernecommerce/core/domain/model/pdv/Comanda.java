package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comanda de mesa (PDV-F009): pedidos incrementais acumulados numa sessão de caixa aberta por
 * horas — o caso do lounge de narguilé, distinto da venda pontual de balcão que {@code Order} já
 * cobre. {@code ABERTA} recebe itens um a um (cada um já debita estoque na hora, ver
 * {@code ComandaService.addItem}); fecha virando um {@code Order} de verdade
 * ({@link ComandaStatus#FECHADA}) ou é abandonada sem cobrança ({@link ComandaStatus#CANCELADA}).
 *
 * <h2>Por que não é um {@code Order} desde o início</h2>
 * <p>{@code Order.items} é uma lista fechada no nascimento do pedido — não há como acrescentar item
 * depois. A comanda precisa exatamente do oposto: crescer aos poucos, por horas, antes de o pedido
 * existir de verdade. Ela só vira {@code Order} no fechamento, quando os itens acumulados são
 * convertidos de uma vez (ver {@code ComandaService.closeComanda}).</p>
 *
 * @param warehouseCode depósito da sessão de caixa que abriu a comanda — mesma regra de
 *        {@code CashRegisterSession.warehouseCode} (PDV-C004): o operador não baixa estoque de
 *        depósito alheio pela porta da comanda.
 * @param orderId preenchido só no fechamento. Nulo em {@code ABERTA} e em {@code CANCELADA} —
 *        comanda cancelada nunca vira pedido.
 * @param customerId cliente identificado na abertura da mesa (PDV-F010), opcional. <b>Não</b> se
 *        confunde com {@link #tableOrCustomerLabel}, que é texto livre para achar a mesa na tela
 *        ("Mesa 4", "o rapaz de boné") e nunca foi vínculo de cadastro. É o {@code customerId} que
 *        faz o pedido da mesa sair com nome e gerar cashback, como já acontece no balcão.
 */
public record Comanda(
        Long id,
        Long sessionId,
        String warehouseCode,
        String tableOrCustomerLabel,
        Long customerId,
        ComandaStatus status,
        List<ComandaItem> items,
        Long orderId,
        String openedBy,
        Instant openedAt,
        Instant closedAt) {

    public Comanda {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId é obrigatório");
        }
        if (warehouseCode == null || warehouseCode.isBlank()) {
            throw new IllegalArgumentException("warehouseCode é obrigatório");
        }
        if (tableOrCustomerLabel == null || tableOrCustomerLabel.isBlank()) {
            throw new IllegalArgumentException("tableOrCustomerLabel é obrigatório");
        }
        if (status == null) {
            throw new IllegalArgumentException("status é obrigatório");
        }
        items = items == null ? List.of() : List.copyOf(items);
        if (openedBy == null || openedBy.isBlank()) {
            throw new IllegalArgumentException("openedBy é obrigatório");
        }
        if (openedAt == null) {
            throw new IllegalArgumentException("openedAt é obrigatório");
        }
        // Espelha o CHECK ck_comanda_status_consistency da V104.
        switch (status) {
            case ABERTA -> {
                if (closedAt != null || orderId != null) {
                    throw new IllegalArgumentException(
                            "comanda ABERTA não pode ter closedAt nem orderId: closedAt=" + closedAt
                                    + ", orderId=" + orderId);
                }
            }
            case FECHADA -> {
                if (closedAt == null || orderId == null) {
                    throw new IllegalArgumentException(
                            "comanda FECHADA exige closedAt e orderId: closedAt=" + closedAt
                                    + ", orderId=" + orderId);
                }
            }
            case CANCELADA -> {
                if (closedAt == null) {
                    throw new IllegalArgumentException("comanda CANCELADA exige closedAt");
                }
                if (orderId != null) {
                    throw new IllegalArgumentException(
                            "comanda CANCELADA não pode ter orderId — nunca virou pedido: orderId=" + orderId);
                }
            }
        }
    }

    /** Abre uma comanda nova, vazia, na sessão informada. */
    public static Comanda open(Long sessionId, String warehouseCode, String tableOrCustomerLabel, String openedBy) {
        return open(sessionId, warehouseCode, tableOrCustomerLabel, null, openedBy);
    }

    /** Abre uma comanda nova, vazia, vinculada a um cliente do CRM (PDV-F010). */
    public static Comanda open(Long sessionId, String warehouseCode, String tableOrCustomerLabel,
            Long customerId, String openedBy) {
        return new Comanda(null, sessionId, warehouseCode, tableOrCustomerLabel, customerId, ComandaStatus.ABERTA,
                List.of(), null, openedBy, Instant.now(), null);
    }

    /** Reconstitui uma comanda a partir de persistência. */
    public static Comanda of(Long id, Long sessionId, String warehouseCode, String tableOrCustomerLabel,
            ComandaStatus status, List<ComandaItem> items, Long orderId, String openedBy, Instant openedAt,
            Instant closedAt) {
        return of(id, sessionId, warehouseCode, tableOrCustomerLabel, null, status, items, orderId,
                openedBy, openedAt, closedAt);
    }

    /** Reconstitui uma comanda a partir de persistência, com o cliente vinculado (PDV-F010). */
    public static Comanda of(Long id, Long sessionId, String warehouseCode, String tableOrCustomerLabel,
            Long customerId, ComandaStatus status, List<ComandaItem> items, Long orderId, String openedBy,
            Instant openedAt, Instant closedAt) {
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, status, items, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * Acrescenta um item à comanda aberta. Cópia — a comanda permanece imutável.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}. O service checa isso
     *         antes, com um erro tipado (409) — esta é a rede de segurança do domínio, mesmo
     *         padrão de {@code CashRegisterSession.closedWith}.
     */
    public Comanda withAddedItem(ComandaItem item) {
        requireOpen();
        List<ComandaItem> newItems = new ArrayList<>(items);
        newItems.add(item);
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, status, newItems, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * Remove uma linha da comanda aberta e, junto com ela, as {@code TROCA} penduradas nela
     * (PDV-F012). Cópia — a comanda permanece imutável.
     *
     * <p><b>A cascata da troca não é conveniência, é consistência.</b> {@code linked_item_id} é uma
     * FK auto-referente: deixar a filha para trás produziria uma linha apontando para um id que não
     * existe mais. E a {@code TROCA} não tem existência própria — ela é a troca de sabor <i>de</i>
     * um consumo livre, sempre cortesia, sempre de valor zero. Sem a sessão que a originou, ela não
     * significa nada.</p>
     *
     * <p><b>O {@code SABOR_EXTRA} é o contrário, e por isso não é arrastado:</b> é linha própria e
     * <b>pode estar sendo cobrada</b> (o segundo sabor de um duplo sem promo). Apagá-lo em silêncio
     * tiraria dinheiro da conta sem o operador ter pedido. Quem decide sobre uma linha cobrada é
     * quem opera o caixa — o service recusa e manda removê-la explicitamente antes.</p>
     *
     * @return a comanda sem a linha e sem as trocas dela
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA} — rede de segurança do
     *         domínio, como em {@link #withAddedItem}
     * @throws IllegalArgumentException se o item não estiver nesta comanda
     */
    public Comanda withRemovedItem(Long itemId) {
        requireOpen();
        if (itemId == null) {
            throw new IllegalArgumentException("itemId é obrigatório para remover linha da comanda");
        }
        boolean exists = items.stream().anyMatch(i -> itemId.equals(i.id()));
        if (!exists) {
            throw new IllegalArgumentException("item " + itemId + " não pertence à comanda " + id);
        }
        List<ComandaItem> newItems = new ArrayList<>(items.stream()
                .filter(i -> !itemId.equals(i.id()))
                .filter(i -> !(itemId.equals(i.linkedItemId()) && i.mode() == ConsumptionMode.TROCA))
                .toList());
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, status, newItems, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * As linhas que {@link #withRemovedItem} levaria junto — as {@code TROCA} penduradas nesta.
     *
     * <p>Existe separada porque o service precisa saber <b>quais</b> saíram para devolver o estoque
     * de cada uma: a comanda depois da remoção não tem mais essa informação.</p>
     */
    public List<ComandaItem> itemsRemovedWith(Long itemId) {
        return items.stream()
                .filter(i -> itemId.equals(i.id())
                        || (itemId.equals(i.linkedItemId()) && i.mode() == ConsumptionMode.TROCA))
                .toList();
    }

    /**
     * Linhas <b>cobradas</b> penduradas nesta — os {@code SABOR_EXTRA}. Não são arrastadas pela
     * remoção; a existência delas a impede. Ver {@link #withRemovedItem}.
     */
    public List<ComandaItem> chargedChildrenOf(Long itemId) {
        return items.stream()
                .filter(i -> itemId.equals(i.linkedItemId()) && i.mode() != ConsumptionMode.TROCA)
                .toList();
    }

    /**
     * Fecha a comanda, vinculando o {@code Order} gerado a partir dos itens acumulados.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     */
    public Comanda closed(Long orderId, Instant closedAt) {
        requireOpen();
        if (orderId == null) {
            throw new IllegalArgumentException("orderId é obrigatório ao fechar a comanda");
        }
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt é obrigatório ao fechar a comanda");
        }
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, ComandaStatus.FECHADA,
                items, orderId, openedBy, openedAt, closedAt);
    }

    /**
     * Abandona a comanda sem cobrança — nunca vira pedido. O estorno do estoque já debitado item a
     * item é responsabilidade do service, mesma divisão de trabalho de {@code Order.cancelled}.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     */
    public Comanda cancelled(Instant closedAt) {
        requireOpen();
        if (closedAt == null) {
            throw new IllegalArgumentException("closedAt é obrigatório ao cancelar a comanda");
        }
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, ComandaStatus.CANCELADA,
                items, null, openedBy, openedAt, closedAt);
    }

    /**
     * Soma dos subtotais das linhas <b>ainda não cobradas</b> — o que falta pagar.
     *
     * <p>Antes de PDV-F017 era a soma de tudo, e as duas leituras coincidiam porque a conta só podia
     * ser fechada inteira. Com conta dividida elas divergem, e é esta que a tela precisa: depois de
     * um fechamento parcial o operador quer ver o saldo restante da mesa, não o total consumido.</p>
     */
    public BigDecimal runningTotal() {
        // PDV-F019: líquido do desconto do kit montável — é o que a mesa deve.
        return openItems().stream().map(ComandaItem::netSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** As linhas que nenhum pedido cobrou ainda (PDV-F017). */
    public List<ComandaItem> openItems() {
        return items.stream().filter(ComandaItem::isOpen).toList();
    }

    /** Nenhuma linha em aberto: a mesa foi paga por inteiro, ainda que em várias contas. */
    public boolean isFullyCharged() {
        return !items.isEmpty() && openItems().isEmpty();
    }

    /**
     * Marca as linhas informadas como cobradas pelo pedido dado (PDV-F017). Cópia — a comanda
     * permanece imutável.
     *
     * <p>Não muda o status: quem decide se a mesa acabou é o service, olhando
     * {@link #isFullyCharged()} depois desta chamada. Manter as duas coisas separadas é o que deixa
     * o fechamento parcial caber no {@code ck_comanda_status_consistency} da V104 — enquanto sobra
     * linha aberta a comanda segue {@code ABERTA} com {@code order_id} nulo, exatamente como o CHECK
     * exige.</p>
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     * @throws IllegalArgumentException se algum id não pertencer à comanda ou já estiver cobrado
     */
    public Comanda withItemsClosedIn(Long chargedInOrderId, List<Long> itemIds) {
        requireOpen();
        if (itemIds == null || itemIds.isEmpty()) {
            throw new IllegalArgumentException("itemIds é obrigatório para fechar linhas da comanda");
        }
        Set<Long> alvo = new HashSet<>(itemIds);
        Set<Long> abertos = openItems().stream().map(ComandaItem::id).collect(Collectors.toSet());
        if (!abertos.containsAll(alvo)) {
            throw new IllegalArgumentException(
                    "itemIds contém linha que não está aberta nesta comanda: " + itemIds);
        }
        List<ComandaItem> newItems = items.stream()
                .map(i -> alvo.contains(i.id()) ? i.closedIn(chargedInOrderId) : i)
                .toList();
        // O parâmetro se chama chargedInOrderId, e não orderId, de propósito: o pedido que cobrou as
        // LINHAS não é o `orderId` da COMANDA, que só existe quando a mesa é encerrada. Nomeá-lo
        // igual sombreava o componente do record e gravava o pedido no cabeçalho de uma comanda que
        // segue ABERTA — o compact constructor recusa, e com razão.
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, status, newItems,
                orderId, openedBy, openedAt, closedAt);
    }

    /**
     * Troca o rótulo da mesa (PDV-F016) — o cliente mudou de lugar no salão. Nada mais muda: os
     * itens, o depósito e a sessão de origem seguem os mesmos, porque nada de físico aconteceu.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     */
    /**
     * PDV-F020 — vincula (ou troca, ou remove com {@code null}) o cliente do CRM de uma mesa já
     * aberta. Antes disto o cliente só entrava na abertura: quem chegava sem cadastro e se
     * identificava no meio da noite saía como venda anônima.
     */
    public Comanda withCustomer(Long newCustomerId) {
        requireOpen();
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, newCustomerId, status, items,
                orderId, openedBy, openedAt, closedAt);
    }

    public Comanda withLabel(String newLabel) {
        requireOpen();
        if (newLabel == null || newLabel.isBlank()) {
            throw new IllegalArgumentException("tableOrCustomerLabel é obrigatório");
        }
        return new Comanda(id, sessionId, warehouseCode, newLabel, customerId, status, items, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * PDV-F023 — há narguilé desta mesa ainda no salão: alguma linha de sessão não recolhida (inclusive
     * a que aguarda pagamento, que já reservou utensílio). É o que impede a mesa de ser encerrada.
     * PDV-F027: não impede mais outra sessão — a mesa aceita sessões em paralelo.
     */
    public boolean hasActiveSession() {
        return items.stream().anyMatch(ComandaItem::isActiveSession);
    }

    /**
     * PDV-F023 — avança o status de uma linha de sessão. Cópia — a comanda permanece imutável.
     *
     * @throws IllegalStateException se a comanda não estiver {@code ABERTA}
     * @throws IllegalArgumentException se o item não estiver nesta comanda
     */
    public Comanda withSessionStatus(Long itemId, SessionStatus next, Instant at) {
        requireOpen();
        boolean exists = items.stream().anyMatch(i -> itemId != null && itemId.equals(i.id()));
        if (!exists) {
            throw new IllegalArgumentException("item " + itemId + " não pertence à comanda " + id);
        }
        List<ComandaItem> newItems = items.stream()
                .map(i -> itemId.equals(i.id()) ? i.withSessionStatus(next, at) : i)
                .toList();
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, status, newItems, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * PDV-F027 — as linhas pagas agora que aguardavam pagamento entram no preparo. Cópia — a comanda
     * permanece imutável.
     */
    public Comanda withSessionsPaid(Collection<Long> paidItemIds, Instant at) {
        requireOpen();
        Set<Long> alvo = new HashSet<>(paidItemIds);
        List<ComandaItem> newItems = items.stream()
                .map(i -> alvo.contains(i.id()) ? i.withSessionPaid(at) : i)
                .toList();
        return new Comanda(id, sessionId, warehouseCode, tableOrCustomerLabel, customerId, status, newItems, orderId,
                openedBy, openedAt, closedAt);
    }

    /**
     * A primeira linha {@code NA_FILA} ligada à sessão {@code rootId}, pela ordem de lançamento — quem
     * entra quando a atual sai. PDV-F027: a fila é por narguilé, não por mesa; com sessões em paralelo,
     * recolher uma não pode começar o rosh de outra.
     */
    public Optional<ComandaItem> nextQueuedSessionOf(Long rootId) {
        return items.stream()
                .filter(i -> i.sessionStatus() == SessionStatus.NA_FILA)
                .filter(i -> rootId != null && rootId.equals(i.linkedItemId()))
                .min(Comparator.comparing(ComandaItem::addedAt)
                        .thenComparing(i -> i.id() == null ? Long.MAX_VALUE : i.id()));
    }

    /**
     * PDV-C026 — a linha pode ir ao preparo: ela própria não deve nada (cortesia, ou já cobrada) e a
     * sessão raiz dela não está esperando pagamento. Desde PDV-F027 nada é preparado antes de pago —
     * o 2º rosh do duplo é cortesia, mas pertence a uma sessão que ainda pode não ter sido paga.
     */
    public boolean isReleasedForPreparation(ComandaItem item) {
        if (!item.courtesy() && item.isOpen()) {
            return false;
        }
        Long rootId = item.mode() == ConsumptionMode.SESSAO ? null : item.linkedItemId();
        return rootId == null || items.stream()
                .filter(i -> rootId.equals(i.id()))
                .noneMatch(i -> i.sessionStatus() == SessionStatus.AGUARDANDO_PAGAMENTO);
    }

    /**
     * PDV-F031 — as linhas que uma junção leva desta mesa para a outra: as ainda não cobradas, mais o
     * <b>grupo inteiro</b> (sessão e roshs ligados a ela) de toda sessão de narguilé que ainda esteja
     * no salão, paga ou não.
     *
     * <p>Grupo inteiro, e não só a linha ativa, porque o utensílio é alocado no id da sessão raiz e a
     * liberação ao recolher procura a raiz <b>na mesma comanda</b>: separar um rosh ativo da raiz já
     * recolhida deixaria o vaso preso para sempre. A linha cobrada que vai junto continua cobrada —
     * o pedido dela segue apontando para esta mesa.</p>
     */
    public List<Long> itemIdsToMoveOnMerge() {
        Set<Long> roots = new HashSet<>();
        for (ComandaItem item : items) {
            if (item.isActiveSession()) {
                roots.add(item.mode() == ConsumptionMode.SESSAO ? item.id() : item.linkedItemId());
            }
        }
        return items.stream()
                .filter(i -> i.isOpen() || roots.contains(i.id())
                        || (i.linkedItemId() != null && roots.contains(i.linkedItemId())))
                .map(ComandaItem::id)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * O pedido que cobrou por último alguma linha desta mesa — é ele que fica no cabeçalho quando a
     * mesa, já toda paga em fechamentos parciais, é encerrada por {@code finish} (PDV-F023).
     */
    public Optional<Long> lastChargedOrderId() {
        return items.stream().map(ComandaItem::closedInOrderId).filter(Objects::nonNull)
                .max(Long::compareTo);
    }

    public boolean isOpen() {
        return status == ComandaStatus.ABERTA;
    }

    private void requireOpen() {
        if (status != ComandaStatus.ABERTA) {
            throw new IllegalStateException("comanda " + id + " não está aberta: " + status);
        }
    }
}
