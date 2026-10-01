package com.cernecommerce.core.ports.out.pdv;

import com.cernecommerce.core.domain.model.pdv.ClosedComanda;
import com.cernecommerce.core.domain.model.pdv.ComandaHistoryFilter;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.pdv.Comanda;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de comandas de mesa (PDV-F009).
 */
public interface ComandaRepository {

    Optional<Comanda> findById(Long id);

    /**
     * Igual a {@link #findById}, mas travando a linha da comanda até o fim da transação (PDV-C008).
     *
     * <p>É o caminho obrigatório de <b>toda mutação</b> de comanda. Sem a trava, dois atendentes
     * operando a mesma mesa — o cenário que PDV-F010 liberou de propósito — leem o mesmo estado e
     * decidem sobre ele em paralelo: o {@code requireOpen} de um passa sobre uma comanda que o
     * outro já fechou, e o item entra numa mesa cujo pedido já foi gerado e pago. O estoque sai, e
     * ninguém é cobrado.</p>
     *
     * <p><b>Por que não {@code @Version}:</b> a versão otimista só protege quando o UPDATE chega a
     * ser emitido, e aqui ele não chega — o Hibernate compara o agregado com o <i>snapshot que ele
     * mesmo carregou</i>, não com o banco, então gravar de volta um estado velho não conta como
     * alteração e nenhuma colisão é detectada. A trava pessimista resolve na leitura, que é onde a
     * decisão é tomada. Molde no repositório: {@code RefreshTokenJpaRepository.findByTokenHashForUpdate}.</p>
     */
    Optional<Comanda> findByIdForUpdate(Long id);

    /**
     * Comandas {@code ABERTA} — as "mesas ocupadas" (PDV-C007).
     *
     * <p>Os dois filtros são <b>opcionais</b>: sem {@code sessionId} a consulta devolve as mesas da
     * loja inteira, que é a decisão do dono (<i>caixa por atendente, mesas compartilhadas</i>) e o
     * que substitui o merge N+1 que o cliente fazia — uma chamada por sessão aberta.</p>
     *
     * <p>Não há filtro por status da sessão de caixa, e isso é deliberado: desde <b>PDV-C005</b> o
     * caixa não fecha com mesa aberta, então comanda {@code ABERTA} já implica sessão {@code OPEN}.
     * Um join com {@code cash_register_session} só repetiria uma invariante que o módulo já
     * garante.</p>
     */
    PageResult<Comanda> findOpen(Long sessionId, String warehouseCode, int page, int size);

    /**
     * Ids das mesas {@code ABERTA} de uma sessão — a guarda de fechamento de caixa (PDV-C005).
     *
     * <p>Não é {@link #findOpen} com filtro: aquela é paginada, e uma guarda que enxerga só uma
     * página deixaria passar o fechamento de um caixa com mais mesas que a página. Devolve id
     * porque é tudo que a exceção precisa mostrar ao operador.</p>
     */
    List<Long> findOpenIdsBySessionId(Long sessionId);

    /**
     * Ids das comandas <b>ABERTAS há mais tempo que {@code cutoff}</b>, para a varredura de mesa
     * esquecida (PDV-F013), das mais antigas para as mais novas e no máximo {@code limit}.
     *
     * <p>Projeta só o id, como {@link #findOpenIdsBySessionId}: a varredura recarrega cada comanda
     * <b>com trava</b> antes de decidir sobre ela, então trazer o agregado aqui seria carregar duas
     * vezes o que vai ser usado uma.</p>
     *
     * <p>O corte é por {@code openedAt} e não por "última atividade": não existe carimbo de último
     * lançamento na comanda, e acrescentá-lo mudaria a tabela por um ganho que a janela de 12h já
     * cobre — uma mesa que recebeu item às 23h continua com {@code openedAt} da noite anterior, mas
     * quem a esqueceu aberta a esqueceu de todo jeito.</p>
     */
    List<Long> findOpenIdsOlderThan(java.time.Instant cutoff, int limit);

    /**
     * Reatribui as linhas {@code itemIds} de uma comanda para outra (PDV-F016, juntar mesas),
     * devolvendo quantas moveram. Quais linhas vão é decisão do domínio
     * ({@code Comanda.itemIdsToMoveOnMerge}, PDV-F031); id que não seja da origem não se move.
     *
     * <p><b>Por que isto não é feito pelo {@code save}.</b> Passar os itens de A dentro do agregado
     * de B faria o {@code save} tratá-los como linhas novas — ids novos — e
     * {@code comanda_item.linked_item_id} é uma FK <b>auto-referente</b> (V114): a {@code TROCA}
     * passaria a apontar para uma linha que não existe mais. Reatribuindo a FK aqui, os ids são
     * preservados e o vínculo continua válido sem remapeamento nenhum. O problema desaparece em vez
     * de ser resolvido.</p>
     *
     * <p><b>Depois desta chamada, o agregado da origem lido antes está velho</b> (PDV-C022): ele
     * ainda contém as linhas movidas, e salvá-lo as reinseriria como linhas novas na origem. Releia.</p>
     */
    int moveItems(Long fromComandaId, Long toComandaId, java.util.Collection<Long> itemIds);

    Comanda save(Comanda comanda);

    /** PDV-F029 — grava quem encerrou a comanda e, no cancelamento, o motivo. */
    void recordClosing(Long comandaId, String closedBy, String cancelReason);

    /** PDV-F029 — comandas encerradas, da mais recente para a mais antiga. */
    PageResult<ClosedComanda> findHistory(ComandaHistoryFilter filter, int page, int size);

    /** PDV-F029 — a comanda com o registro do encerramento (qualquer status). */
    Optional<ClosedComanda> findWithClosing(Long id);

    /** PDV-F029 — FECHADAS no período, para os indicadores. */
    List<ClosedComanda> findClosedBetween(java.time.Instant from, java.time.Instant to, String warehouseCode);
}
