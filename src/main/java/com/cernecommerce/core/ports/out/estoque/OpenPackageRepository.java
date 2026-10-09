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
}
