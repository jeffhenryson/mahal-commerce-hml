package com.cernecommerce.core.domain.model.estoque;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Escopo que diz a {@code EstoqueService} de onde vêm as movimentações gravadas dentro dele.
 *
 * <p><b>Por que um escopo e não mais um parâmetro em {@code adjustStock}.</b> São seis sobrecargas,
 * chamadas de PDV, comanda, pedido, compras e do próprio estoque (embalagem, kit, conversão), e mais
 * de cem verificações de teste sobre elas. A origem é um dado de contexto da operação — como o usuário
 * da requisição —, não da movimentação individual: tudo o que uma venda baixa é "da venda". Mesmo
 * modelo de {@code TransactionSynchronizationManager}: thread-local, sempre aberto com
 * try-with-resources, e o escopo de dentro vence o de fora enquanto está aberto.</p>
 *
 * <p>O escopo também junta os ids das movimentações gravadas, para quem só conhece o id do documento
 * depois de baixar o estoque: a venda de balcão baixa antes de gravar o pedido (validação antes da
 * numeração) e vincula as movimentações ao pedido em seguida.</p>
 */
public final class StockMovementOriginContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private StockMovementOriginContext() {
    }

    /** Abre um escopo; feche com try-with-resources. */
    public static Scope open(StockMovementOrigin origin) {
        Scope scope = new Scope(origin, CURRENT.get());
        CURRENT.set(scope);
        return scope;
    }

    /** Origem do escopo aberto, ou nulo fora de qualquer escopo. */
    public static StockMovementOrigin current() {
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.origin;
    }

    /** Chamado por quem grava a movimentação, para o escopo aberto (se houver) guardar o id. */
    public static void recordMovement(Long movementId) {
        Scope scope = CURRENT.get();
        if (scope != null && movementId != null) {
            scope.movementIds.add(movementId);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final StockMovementOrigin origin;
        private final Scope parent;
        private final List<Long> movementIds = new ArrayList<>();

        private Scope(StockMovementOrigin origin, Scope parent) {
            this.origin = origin;
            this.parent = parent;
        }

        public StockMovementOrigin origin() {
            return origin;
        }

        /** Ids das movimentações gravadas enquanto este escopo era o atual. */
        public List<Long> movementIds() {
            return Collections.unmodifiableList(movementIds);
        }

        @Override
        public void close() {
            if (parent == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(parent);
            }
        }
    }
}
