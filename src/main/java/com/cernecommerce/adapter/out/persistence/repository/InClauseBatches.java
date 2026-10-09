package com.cernecommerce.adapter.out.persistence.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * PDV-C029 — consultas {@code WHERE x IN (:ids)} em lotes. O driver JDBC do Postgres aceita no máximo
 * 32767 parâmetros por consulta, e uma lista acima disso derruba a requisição com 500. Até aqui só o
 * analytics de mesas (até 366 dias de mesas fechadas, vários pedidos por mesa) chegava perto, mas é
 * uma armadilha de qualquer listagem que filtre por uma coleção de ids sem teto.
 */
final class InClauseBatches {

    /** Bem abaixo do teto do driver: sobra folga para os demais parâmetros da consulta. */
    static final int DEFAULT_SIZE = 1000;

    private InClauseBatches() {
    }

    /**
     * Roda {@code query} uma vez por lote de no máximo {@code size} ids e concatena os resultados, na
     * ordem dos lotes. Lista vazia não emite consulta nenhuma — {@code IN ()} nem todo banco aceita.
     */
    static <T, R> List<R> fetch(Collection<T> ids, int size, Function<List<T>, List<R>> query) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<T> all = ids instanceof List<T> list ? list : new ArrayList<>(ids);
        List<R> result = new ArrayList<>();
        for (int from = 0; from < all.size(); from += size) {
            result.addAll(query.apply(all.subList(from, Math.min(from + size, all.size()))));
        }
        return result;
    }

    static <T, R> List<R> fetch(Collection<T> ids, Function<List<T>, List<R>> query) {
        return fetch(ids, DEFAULT_SIZE, query);
    }
}
