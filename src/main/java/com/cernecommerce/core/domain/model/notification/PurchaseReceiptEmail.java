package com.cernecommerce.core.domain.model.notification;

import com.cernecommerce.core.domain.model.notification.OrderStageEmail.Detail;

import java.util.List;

/**
 * O que o comprovante por e-mail mostra além dos itens, para acompanhar o cupom impresso.
 *
 * @param reference número da compra no cupom, como {@code nº 000123}
 * @param summary caixa do início: número, data e mesa
 * @param payments formas de pagamento, com parcelas e troco
 * @param cashback frase do cashback ganho; nula para quem não ganhou
 * @param partiallyRefunded parte da compra foi reembolsada; os valores continuam os originais
 */
public record PurchaseReceiptEmail(
        String reference,
        String preheader,
        List<Detail> summary,
        List<Detail> payments,
        String cashback,
        boolean partiallyRefunded) {

    public PurchaseReceiptEmail {
        summary = summary == null ? List.of() : List.copyOf(summary);
        payments = payments == null ? List.of() : List.copyOf(payments);
    }

    public boolean hasPayments() {
        return !payments.isEmpty();
    }
}
