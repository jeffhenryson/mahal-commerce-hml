package com.cernecommerce.adapter.out.persistence.repository;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDV-C029 — o analytics de mesas passava a lista inteira de ids num {@code IN} só. Com um ano de mesas
 * e vários pedidos parciais por mesa, a lista passava do limite de parâmetros do JDBC do Postgres
 * (32767) e a requisição caía em 500. As consultas agora saem em lotes.
 */
class InClauseBatchesTest {

    @Test
    void fetch_splitsTheIdsIntoBatchesAndConcatenatesTheResults() {
        List<Long> ids = LongStream.rangeClosed(1, 2500).boxed().toList();
        List<Integer> batchSizes = new ArrayList<>();

        List<Long> result = InClauseBatches.fetch(ids, 1000, batch -> {
            batchSizes.add(batch.size());
            return batch;
        });

        assertThat(batchSizes).containsExactly(1000, 1000, 500);
        assertThat(result).containsExactlyElementsOf(ids);
    }

    @Test
    void fetch_withNoIds_neverRunsTheQuery() {
        List<Integer> calls = new ArrayList<>();

        List<Long> result = InClauseBatches.fetch(List.<Long>of(), 1000, batch -> {
            calls.add(batch.size());
            return List.of();
        });

        assertThat(result).isEmpty();
        assertThat(calls).isEmpty();
    }

    /** O default fica bem abaixo do teto do driver, com folga para outros parâmetros na mesma consulta. */
    @Test
    void defaultBatchSize_staysFarBelowThePostgresParameterLimit() {
        assertThat(InClauseBatches.DEFAULT_SIZE).isBetween(1, 32767 / 10);
    }
}
