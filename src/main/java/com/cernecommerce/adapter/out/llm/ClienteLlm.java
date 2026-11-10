package com.cernecommerce.adapter.out.llm;

import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Ouvinte;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Requisicao;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.RespostaTurno;

/**
 * Um provedor de IA. Quem implementa a porta é o {@link RoteadorLlmAdapter}, que escolhe o
 * cliente pela credencial e cuida da reserva; cada cliente só conhece o seu SDK.
 */
interface ClienteLlm {

    ProvedorIa provedor();

    /**
     * Um turno completo com as credenciais dadas.
     *
     * @throws com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException em falha do provedor
     */
    RespostaTurno conversar(AssistantCredentials credenciais, Requisicao requisicao, Ouvinte ouvinte);

    IntegrationTestResult testar(AssistantCredentials credenciais);
}
