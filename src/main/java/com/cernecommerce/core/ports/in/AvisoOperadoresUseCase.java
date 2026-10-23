package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.notification.NotificationType;

/**
 * Aviso em tempo real a quem opera o PDV — notificação gravada mais SSE, depois do commit, para
 * todo usuário com a permissão dada (Salão QR e pedidos do app, ECM-F010).
 */
public interface AvisoOperadoresUseCase {

    /**
     * @param chave agrupa avisos repetidos na mesma transação (um só sai)
     * @param destino rota do front que o aviso abre ao ser clicado
     */
    void avisar(String chave, String permissao, NotificationType tipo, String titulo, String corpo, String destino);
}
