package com.cernecommerce.core.ports.out.notification;

import java.util.Map;

/**
 * Avisa os devs (usuários com ROLE_DEV) de falhas técnicas. Quem chama não espera nada: o envio é
 * assíncrono, repetições do mesmo alerta são agrupadas, e uma falha aqui nunca sobe para o chamador.
 */
public interface DevAlertPort {

    /**
     * Alerta por e-mail e in-app.
     *
     * @param dedupKey alertas com a mesma chave dentro da janela de agrupamento saem uma vez só
     * @param details  linhas "rótulo: valor", na ordem do mapa
     */
    void alert(String category, String dedupKey, String subject, Map<String, String> details);

    /**
     * Falha de entrega de e-mail. Só in-app: avisar por e-mail que o e-mail falhou cairia no mesmo
     * provedor quebrado — e cada falha geraria outra.
     */
    void emailDeliveryFailed(String emailType, String to, String error);
}
