package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.OrderClosureEmail;
import com.cernecommerce.core.domain.model.notification.OrderEmailView;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail.Detail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * O e-mail de cada forma de um pedido acabar. Sem gíria, ao contrário das etapas: aqui o cliente está
 * preocupado com o dinheiro. O motivo digitado em {@code /cancel} e {@code /refund} não entra: é
 * anotação interna, e só o diálogo de recusa avisa o operador de que o cliente vai ler.
 */
public final class EmailEncerramentoPedido {

    private static final String DEVOLUCAO = "a loja devolve por Pix.";

    private EmailEncerramentoPedido() {
    }

    public static OrderClosureEmail recusado(OrderEmailView order, String motivo) {
        boolean temMotivo = motivo != null && !motivo.isBlank();
        return cancelamento("Não conseguimos aceitar seu pedido " + order.orderReference(),
                temMotivo ? "Motivo: " + motivo : "A loja não conseguiu aceitar o pedido.",
                "Não conseguimos aceitar seu pedido",
                "Infelizmente a loja não conseguiu aceitar o pedido " + order.orderReference() + ".",
                order, temMotivo ? List.of(new Detail("Motivo:", motivo)) : List.of());
    }

    public static OrderClosureEmail expirado(OrderEmailView order) {
        return cancelamento("Pedido " + order.orderReference() + " cancelado: o prazo acabou",
                "O pedido não foi pago até o fim do dia.",
                "O prazo do seu pedido acabou",
                "O pedido " + order.orderReference() + " foi cancelado porque não foi pago até o fim do dia.",
                order, List.of());
    }

    public static OrderClosureEmail peloCliente(OrderEmailView order) {
        return cancelamento("Pedido " + order.orderReference() + " cancelado",
                "Cancelamos o pedido como você pediu.",
                "Pedido cancelado",
                "Cancelamos o pedido " + order.orderReference() + ", como você pediu.",
                order, List.of());
    }

    public static OrderClosureEmail pelaLoja(OrderEmailView order) {
        return cancelamento("Pedido " + order.orderReference() + " cancelado",
                "A loja cancelou o seu pedido.",
                "Pedido cancelado",
                "A loja cancelou o pedido " + order.orderReference() + ".",
                order, List.of());
    }

    /**
     * @param order o pedido inteiro, com o cashback usado
     * @param dinheiro o que foi devolvido neste reembolso, sem a parte paga com cashback
     * @param valorItens soma dos itens deste reembolso, com ou sem dinheiro devolvido
     * @param parcial parte do pedido continua valendo
     * @param marcado o pedido tem pagamento marcado (fiado)
     */
    public static OrderClosureEmail reembolso(OrderEmailView order, BigDecimal dinheiro, BigDecimal valorItens,
            boolean parcial, boolean marcado) {
        boolean devolveDinheiro = dinheiro != null && dinheiro.signum() > 0;
        if (!devolveDinheiro && marcado) {
            return parcial ? marcadoParcial(order, valorItens) : marcadoTotal(order);
        }
        String ref = order.orderReference();
        List<Detail> details = new ArrayList<>();
        if (devolveDinheiro) {
            details.add(new Detail("Valor:", EmailFormat.money(dinheiro)));
            details.add(new Detail("Devolução:", DEVOLUCAO));
        }
        String combinar = devolveDinheiro ? "Fale com a gente pelo WhatsApp para combinar a devolução." : null;
        String preheader = devolveDinheiro ? "Registramos o reembolso de " + EmailFormat.money(dinheiro) + "."
                : "Registramos o reembolso do seu pedido.";
        String totalLabel = !parcial ? null : order.hasValue(order.cashbackRedeemed()) ? "Valor dos itens" : "Valor reembolsado";
        return new OrderClosureEmail(
                parcial ? "Reembolso parcial do pedido " + ref : "Reembolso do pedido " + ref,
                preheader,
                parcial ? "Reembolsamos parte do seu pedido" : "Seu reembolso foi registrado",
                parcial ? "Registramos o reembolso de parte do pedido " + ref + ". O restante continua valendo."
                        : "Registramos o reembolso do pedido " + ref + ".",
                juntar(combinar, cashbackDoReembolso(order, parcial)),
                details, true, false, devolveDinheiro, totalLabel);
    }

    private static OrderClosureEmail marcadoTotal(OrderEmailView order) {
        String ref = order.orderReference();
        return new OrderClosureEmail("Reembolso do pedido " + ref, "O valor marcado foi cancelado.",
                "Sua compra marcada foi cancelada",
                "Cancelamos o valor marcado do pedido " + ref + ". Você não deve mais nada por ele.",
                cashbackDoReembolso(order, false), List.of(), true, false, false, null);
    }

    private static OrderClosureEmail marcadoParcial(OrderEmailView order, BigDecimal valorItens) {
        String ref = order.orderReference();
        String valor = EmailFormat.money(valorItens);
        return new OrderClosureEmail("Reembolso parcial do pedido " + ref, "Abatemos " + valor + " do valor marcado.",
                "Ajustamos sua compra marcada",
                "Reembolsamos parte do pedido " + ref + " e abatemos " + valor + " do valor marcado.",
                cashbackDoReembolso(order, true), List.of(), true, false, false, "Valor abatido");
    }

    private static OrderClosureEmail cancelamento(String subject, String preheader, String title, String body,
            OrderEmailView order, List<Detail> details) {
        // Antes de a loja confirmar, o cliente da entrega pode já ter feito o Pix.
        String dinheiro = order.delivery() ? "Se você já fez o Pix, fale com a loja para receber o valor de volta."
                : "Nenhum valor foi cobrado.";
        return new OrderClosureEmail(subject, preheader, title, body, juntar(dinheiro, cashbackUsado(order)),
                details, false, true, false, null);
    }

    private static String cashbackUsado(OrderEmailView order) {
        return !order.hasValue(order.cashbackRedeemed()) ? null
                : "O cashback de " + EmailFormat.money(order.cashbackRedeemed())
                        + " usado neste pedido voltou para o seu saldo. A parte que já tinha vencido não volta.";
    }

    private static String cashbackDoReembolso(OrderEmailView order, boolean parcial) {
        if (!order.hasValue(order.cashbackRedeemed())) {
            return null;
        }
        return parcial ? "A parte paga com cashback voltou para o seu saldo. A parte que já tinha vencido não volta."
                : cashbackUsado(order);
    }

    private static String juntar(String primeira, String segunda) {
        if (primeira == null) {
            return segunda;
        }
        return segunda == null ? primeira : primeira + " " + segunda;
    }
}
