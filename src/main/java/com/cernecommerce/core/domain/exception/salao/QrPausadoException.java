package com.cernecommerce.core.domain.exception.salao;

/** A loja pausou as novas solicitações pelo QR da mesa na Central de Pedidos (ECM-F013). */
public class QrPausadoException extends RuntimeException {

    public QrPausadoException() {
        super("a loja não está recebendo pedidos pelo QR da mesa agora — chame um atendente");
    }
}
