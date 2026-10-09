package com.cernecommerce.core.domain.model.pdv;

import com.cernecommerce.core.domain.model.pagamento.PaymentMethod;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Pagamento de uma venda feita offline no balcão (PDV-F043).
 *
 * <p>Só {@code DINHEIRO}, {@code DEBITO} e {@code CREDITO}: são os que acontecem sem rede (gaveta e
 * maquininha). PIX precisa confirmar o recebimento, marcado precisa consultar o limite do cliente, e o
 * PIX de gateway nunca é lançado pelo operador.</p>
 */
public record OfflineSalePayment(PaymentMethod method, BigDecimal amount, Integer installments) {

    public static final Set<PaymentMethod> ALLOWED_OFFLINE =
            Set.of(PaymentMethod.DINHEIRO, PaymentMethod.DEBITO, PaymentMethod.CREDITO);

    public boolean allowedOffline() {
        return ALLOWED_OFFLINE.contains(method);
    }
}
