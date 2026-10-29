package com.cernecommerce.core.domain.exception.ecommerce;

import java.math.BigDecimal;

/**
 * O checkout do app esbarrou na configuração do recebimento (ECM-F013). O {@link #codigo()} vira o
 * {@code errorCode} da resposta, para o app mostrar a mensagem certa sem interpretar texto.
 */
public class PedidoAppIndisponivelException extends RuntimeException {

    private final String codigo;

    private PedidoAppIndisponivelException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public static PedidoAppIndisponivelException lojaFechada() {
        return new PedidoAppIndisponivelException("LOJA_FECHADA", "a loja não está recebendo pedidos pelo app agora");
    }

    public static PedidoAppIndisponivelException modalidadeIndisponivel(String modalidade) {
        return new PedidoAppIndisponivelException("MODALIDADE_INDISPONIVEL",
                modalidade + " não está disponível no momento");
    }

    public static PedidoAppIndisponivelException pedidoMinimo(BigDecimal minimo) {
        return new PedidoAppIndisponivelException("PEDIDO_MINIMO",
                "o pedido mínimo para entrega é R$ " + minimo.toPlainString());
    }

    public static PedidoAppIndisponivelException foraDaArea(String bairro) {
        return new PedidoAppIndisponivelException("FORA_DA_AREA",
                "a loja não entrega no bairro " + (bairro == null || bairro.isBlank() ? "informado" : bairro));
    }

    public String codigo() {
        return codigo;
    }
}
