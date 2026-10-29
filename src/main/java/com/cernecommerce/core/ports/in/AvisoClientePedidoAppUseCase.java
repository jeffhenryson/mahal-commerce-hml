package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.pedido.Order;

/**
 * Aviso ao cliente de cada etapa do pedido do app (ECM-F010, ECM-F013): e-mail e notificação no app,
 * depois do commit.
 */
public interface AvisoClientePedidoAppUseCase {

    /** Etapa nova — {@code mensagem} vem de {@code MensagemCliente}. E-mail e notificação. */
    void etapa(Order order, String mensagem);

    /** Só a notificação no app, quando o e-mail sai por outro caminho (a recusa). */
    void soNotificacao(Order order, String mensagem);

    /** Cancelado pela loja ou expirado — e-mail de cancelamento e notificação. */
    void cancelamento(Order order, String motivo);
}
