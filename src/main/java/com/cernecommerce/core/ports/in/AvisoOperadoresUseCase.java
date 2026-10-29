package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.notification.NotificationType;

import java.util.List;

/**
 * Aviso em tempo real a quem opera o PDV — notificação gravada mais SSE, depois do commit, para
 * todo usuário com a permissão dada (Salão QR e pedidos do app, ECM-F010).
 */
public interface AvisoOperadoresUseCase {

    /**
     * @param chave agrupa avisos repetidos na mesma transação (um só sai)
     * @param destino rota do front que o aviso abre ao ser clicado
     */
    default void avisar(String chave, String permissao, NotificationType tipo, String titulo, String corpo,
            String destino) {
        avisar(chave, List.of(permissao), tipo, titulo, corpo, destino);
    }

    /** Para quem tem qualquer uma das permissões; quem tem mais de uma recebe um aviso só. */
    void avisar(String chave, List<String> permissoes, NotificationType tipo, String titulo, String corpo,
            String destino);
}
