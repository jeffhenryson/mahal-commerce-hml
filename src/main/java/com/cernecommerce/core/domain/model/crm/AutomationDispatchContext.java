package com.cernecommerce.core.domain.model.crm;

import com.cernecommerce.core.domain.model.pedido.Order;

import java.util.Map;

/**
 * O que um disparo sabe sobre a ocorrência: nome do evento, cliente (nulo nos eventos da loja),
 * pedido (só em eventos de pedido/venda), contexto extra, blocos de variáveis de topo do evento
 * ({@code produto.*} no pedido de cliente) e se é envio de teste.
 */
public record AutomationDispatchContext(String evento, AutomationRecipient cliente, Order order,
        Map<String, Object> contexto, Map<String, Object> variaveis, boolean teste) {

    public AutomationDispatchContext {
        contexto = contexto == null ? Map.of() : contexto;
        variaveis = variaveis == null ? Map.of() : variaveis;
    }

    public AutomationDispatchContext(String evento, AutomationRecipient cliente, Order order,
            Map<String, Object> contexto, boolean teste) {
        this(evento, cliente, order, contexto, Map.of(), teste);
    }
}
