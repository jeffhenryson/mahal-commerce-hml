package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;

/**
 * O texto que o cliente lê sobre a etapa do pedido do app (ECM-F013) — no e-mail, na notificação do
 * app e no {@code GET /shop/orders/{id}}. Um lugar só, para os três dizerem a mesma coisa.
 */
public final class MensagemCliente {

    private MensagemCliente() {
    }

    /**
     * @param pixChave chave Pix da loja (Central de Pedidos); nula, o texto diz que a loja manda pelo WhatsApp.
     */
    public static String para(AtendimentoPedidoApp atendimento, Order order, String pixChave) {
        boolean pago = order.status() == OrderStatus.CONCLUIDO;
        return switch (atendimento.status()) {
            case PENDENTE -> "Pedido recebido — aguardando a loja aceitar";
            case ACEITO -> {
                if (!atendimento.isEntrega()) {
                    yield "Pedido aceito — já estamos separando";
                }
                yield pago ? pagamentoConfirmado(atendimento) : aceiteDaEntrega(order, pixChave);
            }
            case PRONTO -> atendimento.isEntrega() ? "Pedido separado, aguardando o entregador"
                    : "Pedido pronto para retirada";
            case SAIU_PARA_ENTREGA -> saiu(order);
            case RETIRADO -> "Pedido retirado — obrigado!";
            case ENTREGUE -> "Pedido entregue";
            case RECUSADO -> "Pedido recusado pela loja" + (atendimento.motivo() == null ? "" : ": " + atendimento.motivo());
            case CANCELADO -> "Pedido cancelado";
            case EXPIRADO -> "Pedido cancelado — o prazo do dia acabou sem pagamento";
        };
    }

    /** Liquidado no caixa. Na retirada o pedido sai junto; na entrega, a loja separa e despacha. */
    public static String pagamentoConfirmado(AtendimentoPedidoApp atendimento) {
        return atendimento.isEntrega() ? "Pagamento confirmado — estamos separando seu pedido"
                : "Pagamento confirmado — pedido retirado. Obrigado!";
    }

    private static String aceiteDaEntrega(Order order, String pixChave) {
        String total = "Pedido aceito — total R$ " + order.totalPayable().toPlainString() + " com o frete. ";
        return pixChave == null
                ? total + "A loja vai te mandar o Pix pelo WhatsApp"
                : total + "Pague por Pix na chave " + pixChave + " e a loja confirma o pagamento";
    }

    private static String saiu(Order order) {
        String link = order.delivery() == null ? null : order.delivery().trackingUrl();
        return link == null ? "Pedido saiu para entrega" : "Pedido saiu para entrega — acompanhe a viagem: " + link;
    }
}
