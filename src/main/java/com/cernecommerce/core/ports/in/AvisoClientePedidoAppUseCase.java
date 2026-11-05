package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.notification.OrderStageEmail;
import com.cernecommerce.core.domain.model.pedido.Order;

/**
 * Aviso ao cliente de cada etapa do pedido do app (ECM-F010, ECM-F013): e-mail e notificação no app,
 * depois do commit.
 */
public interface AvisoClientePedidoAppUseCase {

    /** Etapa nova: {@code email} vem de {@code EmailEtapaPedido} e {@code mensagem}, de {@code MensagemCliente}. */
    void etapa(Order order, String mensagem, OrderStageEmail email);

    /** Só a notificação no app, quando o e-mail sai por outro caminho (a recusa). */
    void soNotificacao(Order order, String mensagem);

    /** Expirado no fim do dia — e-mail de cancelamento e notificação. */
    void cancelamento(Order order, String motivo);
}
