package com.cernecommerce.core.domain.model.crm;

import java.time.Duration;
import java.time.Instant;

/**
 * Segmento do cliente (V183), um RFM simplificado sobre os pedidos pagos. A mesma regra está na
 * V183 em SQL, só para o backfill.
 * <ol>
 *   <li><b>VIP</b>: tem a tag VIP (a mesma do "Marcar", CRM-F010) ou foi marcado VIP à mão;</li>
 *   <li><b>Novo</b>: nenhum pedido pago;</li>
 *   <li><b>Inativo</b>: última compra há mais de {@value #DIAS_INATIVO} dias;</li>
 *   <li><b>Em Risco</b>: última compra há mais de {@value #DIAS_EM_RISCO} dias (o mesmo corte do
 *       estágio Inativo do funil, um aviso antes);</li>
 *   <li><b>Recorrente</b>: {@value #PEDIDOS_RECORRENTE} ou mais pedidos pagos;</li>
 *   <li><b>Novo</b>: o resto (comprou, mas pouco).</li>
 * </ol>
 */
public final class CustomerSegmentRule {

    public static final int DIAS_INATIVO = 90;
    public static final int DIAS_EM_RISCO = 45;
    public static final int PEDIDOS_RECORRENTE = 3;

    private CustomerSegmentRule() {
    }

    public static String segmento(boolean vip, long pedidosPagos, Instant ultimaCompra, Instant agora) {
        if (vip) {
            return "VIP";
        }
        if (pedidosPagos == 0 || ultimaCompra == null) {
            return "Novo";
        }
        Duration desde = Duration.between(ultimaCompra, agora);
        if (desde.compareTo(Duration.ofDays(DIAS_INATIVO)) > 0) {
            return "Inativo";
        }
        if (desde.compareTo(Duration.ofDays(DIAS_EM_RISCO)) > 0) {
            return "Em Risco";
        }
        return pedidosPagos >= PEDIDOS_RECORRENTE ? "Recorrente" : "Novo";
    }
}
