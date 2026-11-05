package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.model.notification.EmailFormat;
import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;

/** O e-mail de cada etapa do pedido. A frase curta do app e do {@code GET /shop/orders/{id}} fica em {@link MensagemCliente}. */
public final class EmailEtapaPedido {

    private EmailEtapaPedido() {
    }

    /**
     * Etapa do atendimento do pedido do app.
     *
     * @param pixChave chave Pix da loja (Central de Pedidos); nula, o texto diz que a loja manda pelo WhatsApp.
     */
    public static OrderStageEmail para(AtendimentoPedidoApp atendimento, Order order, String pixChave) {
        return switch (atendimento.status()) {
            case ACEITO -> {
                if (!atendimento.isEntrega()) {
                    yield aceitoNaRetirada(order);
                }
                yield order.status() == OrderStatus.CONCLUIDO ? pago(order) : aceitoNaEntrega(order, pixChave);
            }
            case PRONTO -> atendimento.isEntrega() ? separado(order) : prontoParaRetirada(order);
            case SAIU_PARA_ENTREGA -> saiu(order);
            case RETIRADO -> retirado(order);
            case ENTREGUE -> entregue(order);
            // Estas etapas não mandam este e-mail: confirmação e cancelamento têm os seus.
            case PENDENTE, RECUSADO, CANCELADO, EXPIRADO -> atualizado(order);
        };
    }

    /** Liquidado no caixa. Na retirada o pedido sai junto; na entrega, a loja separa e despacha. */
    public static OrderStageEmail pagamentoConfirmado(AtendimentoPedidoApp atendimento, Order order) {
        return atendimento.isEntrega() ? pago(order) : retirado(order);
    }

    /** Mudança de {@link OrderStatus} fora do atendimento do app: pagamento online e alteração manual. */
    public static OrderStageEmail paraStatus(Order order, String status) {
        boolean entrega = order.delivery() != null && order.delivery().isEntrega();
        return switch (status) {
            case "PAGO" -> pago(order);
            case "SEPARADO" -> entrega ? separado(order) : prontoParaRetirada(order);
            case "ENVIADO" -> saiu(order);
            case "ENTREGUE" -> entregue(order);
            case "CONCLUIDO" -> concluido(order);
            default -> atualizado(order);
        };
    }

    private static OrderStageEmail aceitoNaRetirada(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " aceito!",
                        "A loja já está separando tudo. Avisamos quando ficar pronto.",
                        "Pedido aceito!",
                        "A loja aceitou seu pedido e já está separando tudo. A gente avisa quando estiver pronto.")
                .items().tracking().build();
    }

    private static OrderStageEmail aceitoNaEntrega(Order order, String pixChave) {
        String total = EmailFormat.money(order.totalPayable());
        String instrucao = pixChave == null ? "O Pix chega pelo WhatsApp." : "Faça o Pix e a loja confirma o pagamento.";
        OrderStageEmail.Builder email = OrderStageEmail.builder("Pedido " + ref(order) + " aceito: falta o Pix",
                        "O total com o frete ficou em " + total + ".",
                        "Pedido aceito! Falta só o Pix",
                        "A loja aceitou seu pedido e fechou o frete. " + instrucao)
                .detail("Total a pagar:", total)
                .items().tracking();
        if (pixChave != null) {
            email.detail("Chave Pix:", pixChave);
        }
        return email.build();
    }

    private static OrderStageEmail pago(Order order) {
        return OrderStageEmail.builder("Pagamento do pedido " + ref(order) + " confirmado",
                        "Recebemos o pagamento e já estamos separando tudo.",
                        "Pagamento confirmado!",
                        "Recebemos seu pagamento e já estamos separando seu pedido " + ref(order) + ".")
                .items().tracking().build();
    }

    private static OrderStageEmail prontoParaRetirada(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " pronto para retirada",
                        "Pode vir buscar! O pagamento é no caixa.",
                        "Pode vir buscar!",
                        "Seu pedido " + ref(order) + " está separado e te esperando aqui na loja.")
                .pickup()
                .detail("Pagamento:", "no caixa, na hora de retirar. Total de "
                        + EmailFormat.money(order.totalPayable()) + ".")
                .tracking().build();
    }

    private static OrderStageEmail separado(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " separado",
                        "Agora é só o entregador pegar.",
                        "Pedido separado!",
                        "Seu pedido " + ref(order) + " está pronto. Agora é só o entregador pegar.")
                .tracking().build();
    }

    private static OrderStageEmail saiu(Order order) {
        String corrida = order.delivery() == null ? null : order.delivery().trackingUrl();
        return OrderStageEmail.builder("Pedido " + ref(order) + " saiu para entrega",
                        "Seu pedido está a caminho!",
                        "Seu pedido está a caminho!",
                        "O pedido " + ref(order) + " saiu daqui agora."
                                + (corrida == null ? " Logo mais chega aí." : " Acompanhe a viagem pelo botão."))
                .tracking().ride(corrida).build();
    }

    private static OrderStageEmail retirado(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " retirado. Valeu!",
                        "Pagamento confirmado. Até a próxima!",
                        "Valeu pela compra!",
                        "Pagamento confirmado e pedido " + ref(order) + " retirado. Até a próxima!")
                .items().build();
    }

    private static OrderStageEmail entregue(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " entregue",
                        "Chegou! Valeu pela compra.",
                        "Chegou!",
                        "Seu pedido " + ref(order) + " foi entregue. Valeu pela compra!")
                .items().build();
    }

    private static OrderStageEmail concluido(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " concluído",
                        "Valeu pela compra!",
                        "Pedido concluído!",
                        "Seu pedido " + ref(order) + " foi concluído. Valeu pela compra!")
                .items().build();
    }

    private static OrderStageEmail atualizado(Order order) {
        return OrderStageEmail.builder("Pedido " + ref(order) + " atualizado",
                        "Teve novidade no seu pedido.",
                        "Teve novidade no seu pedido",
                        "Seu pedido " + ref(order) + " foi atualizado. Confira os detalhes no app.")
                .items().tracking().build();
    }

    private static String ref(Order order) {
        return "#" + order.id();
    }
}
