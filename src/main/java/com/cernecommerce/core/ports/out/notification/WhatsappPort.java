package com.cernecommerce.core.ports.out.notification;

import com.cernecommerce.core.domain.model.config.WhatsappSendResult;

import java.util.List;

/**
 * WhatsApp Cloud API oficial da Meta (Graph API). As credenciais vêm por chamada — moram cifradas
 * em {@code system_config}, não em propriedades. Implementações nunca lançam: a recusa da Meta, o
 * timeout e o erro de rede viram {@link WhatsappSendResult#failed}.
 */
public interface WhatsappPort {

    /** Checagem barata: lê o número pelo id. Sucesso = token válido e com acesso ao número. */
    WhatsappSendResult checkPhoneNumber(String phoneNumberId, String accessToken);

    /**
     * Envia uma mensagem de template aprovado. {@code bodyParameters} preenche {{1}}, {{2}}... do
     * corpo, na ordem; vazio = template sem variáveis.
     */
    WhatsappSendResult sendTemplate(String phoneNumberId, String accessToken, String to, String template,
            String language, List<String> bodyParameters);
}
