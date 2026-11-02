package com.cernecommerce.core.domain.model.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * O cliente com o que vem de outros domínios (pendência 8): LTV e pedidos pagos, cashback
 * disponível e tags. Montado em lote para a página inteira.
 *
 * @param ltv soma do líquido dos pedidos pagos; zero quando nunca comprou
 * @param ultimaCompraEm nulo quando nunca comprou
 */
public record CustomerSummary(Customer customer, List<String> tags, BigDecimal ltv, long pedidosCount,
        BigDecimal ticketMedio, Instant ultimaCompraEm, BigDecimal cashback) {

    public CustomerSummary {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
