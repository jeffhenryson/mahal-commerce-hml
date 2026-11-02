package com.cernecommerce.core.ports.out.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Números do cliente vindos de outros domínios (pedidos, cashback, tags, campanhas), sempre em lote
 * por página de ids: a lista de clientes paga um punhado de consultas por página, nunca uma por linha.
 * Pedido "pago" é o mesmo conjunto da receita de vendas: PAGO, SEPARADO, ENVIADO, ENTREGUE,
 * CONCLUIDO e RESERVADO.
 */
public interface CustomerInsightsRepository {

    /** LTV (soma do líquido), quantidade e data do último pedido pago, por cliente. Ausente = nunca comprou. */
    Map<Long, CustomerStats> statsFor(Collection<Long> customerIds);

    /** Saldo de cashback disponível agora, por cliente. Ausente = zero. */
    Map<Long, BigDecimal> cashbackAvailableFor(Collection<Long> customerIds, Instant now);

    /** Nomes das tags, por cliente, em ordem alfabética. Ausente = sem tag. */
    Map<Long, List<String>> tagNamesFor(Collection<Long> customerIds);

    /** LTV médio de quem já comprou ao menos uma vez. Zero se ninguém comprou. */
    BigDecimal averageLtv();

    /** Soma do cashback disponível da base inteira: o passivo que a loja deve aos clientes. */
    BigDecimal cashbackAvailableTotal(Instant now);

    /** Disparos de automação com canal WhatsApp (ou AMBOS) marcados como ENVIADO no intervalo. */
    long whatsappDispatchesBetween(Instant from, Instant to);

    /** Quantos clientes em cada segmento. */
    Map<String, Long> countBySegmento();

    /**
     * Recalcula o segmento de quem não está com segmento manual. {@code customerId} nulo: a base
     * inteira. Devolve quantos clientes foram regravados.
     */
    int recalculateSegments(Long customerId, Instant now);

    record CustomerStats(Long customerId, BigDecimal ltv, long pedidos, Instant ultimaCompraEm) {

        public BigDecimal ticketMedio() {
            return pedidos == 0 ? BigDecimal.ZERO
                    : ltv.divide(BigDecimal.valueOf(pedidos), 2, java.math.RoundingMode.HALF_UP);
        }
    }
}
