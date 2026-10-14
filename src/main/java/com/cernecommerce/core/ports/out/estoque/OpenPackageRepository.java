package com.cernecommerce.core.ports.out.estoque;

import com.cernecommerce.core.domain.model.estoque.OpenPackage;

import java.util.List;
import java.util.Optional;

/** Persistência da lata aberta (EST-F027). */
public interface OpenPackageRepository {

    /**
     * A lata em uso de um par SKU/depósito, se houver. No máximo uma existe por par — garantido
     * por índice único parcial, não só pela aplicação.
     */
    Optional<OpenPackage> findOpen(String sku, Long warehouseId);

    /**
     * Igual a {@link #findOpen}, mas travando a linha da lata até o fim da transação (EST-C025).
     *
     * <p>É o caminho obrigatório de <b>toda escrita</b> no contador. Sem a trava, dois atendentes
     * lançando sessão do mesmo sabor leem o mesmo {@code uses} e gravam o mesmo resultado — um uso
     * some, e a lata rende uma sessão a mais do que a realidade.</p>
     *
     * <p><b>Pessimista e não {@code @Version}</b>, como a comanda (PDV-C008): as escritas fazem
     * fila e todas passam, em vez de o atendente que perdeu a corrida tomar 409 no meio do salão
     * por um detalhe de contabilidade de lata.</p>
     *
     * <p>Limite conhecido: quando <b>não há</b> lata aberta não há linha a travar, então duas
     * sessões simultâneas que encontram o par vazio (ou a lata acabando no mesmo instante) abrem
     * cada uma a sua; a segunda colide no índice parcial da V124 e responde 409, com a
     * {@code SAIDA} revertida junto. É a janela da troca de lata, não do uso comum.</p>
     */
    Optional<OpenPackage> findOpenForUpdate(String sku, Long warehouseId);

    /** Todas as latas em uso de um depósito, para a tela de acompanhamento. */
    List<OpenPackage> findAllOpen(Long warehouseId);

    OpenPackage save(OpenPackage openPackage);

    /**
     * EST-F037 — latas fechadas de um depósito com {@code closed_at} em {@code [from, to]} e ao menos
     * um uso, agrupadas por SKU: quantas e a soma dos usos.
     */
    List<ClosedYield> closedYield(Long warehouseId, java.time.Instant from, java.time.Instant to);

    /**
     * EST-F038 — igual a {@link #closedYield}, mas de todos os depósitos. É o rendimento do Salão:
     * as latas fechadas antes de ele existir ficaram no depósito do caixa, e sem elas a média real
     * recomeçaria do zero.
     */
    List<ClosedYield> closedYieldAllWarehouses(java.time.Instant from, java.time.Instant to);

    /**
     * EST-F038 — leva todas as latas em uso para {@code targetWarehouseId}, mantendo usos, abertura e
     * quem abriu, e sem movimento de estoque: a unidade já saiu do saldo quando a lata foi aberta.
     *
     * <p>Antes de mover, onde houver mais de uma lata em uso do mesmo SKU (uma por caixa), fecha as
     * mais antigas com {@code REPLACED} em {@code closedAt}: o destino só comporta uma por SKU
     * (índice parcial da V124), e a mais nova é a que está na mão do atendente.</p>
     *
     * @return quantas latas mudaram de depósito
     */
    int moveAllOpenTo(Long targetWarehouseId, java.time.Instant closedAt);

    record ClosedYield(String sku, long closedCount, long totalSessions) {
    }
}
