package com.cernecommerce.core.domain.model.notification;

import java.util.List;

/**
 * O e-mail de um pedido cancelado ou reembolsado.
 *
 * @param followUp o que acontece com o dinheiro e o cashback; nulo omite o parágrafo
 * @param details linhas da caixa de destaque, como o motivo e o valor devolvido
 * @param refund {@code true} usa a cor da marca, e não a de alerta do cancelamento
 * @param newOrder mostra o botão para fazer um novo pedido
 * @param whatsapp mostra o botão para combinar a devolução com a loja
 * @param totalLabel rótulo da última linha da lista de itens; nulo fica "Total"
 */
public record OrderClosureEmail(
        String subject,
        String preheader,
        String title,
        String body,
        String followUp,
        List<OrderStageEmail.Detail> details,
        boolean refund,
        boolean newOrder,
        boolean whatsapp,
        String totalLabel) {

    public OrderClosureEmail {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public boolean hasCallout() {
        return !details.isEmpty();
    }
}
