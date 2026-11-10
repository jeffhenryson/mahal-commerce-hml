package com.cernecommerce.adapter.out.llm;

import com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;
import com.cernecommerce.core.domain.model.config.ProvedorIa;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Único adaptador da {@link AssistenteLlmPort}: escolhe o cliente (Claude, ChatGPT, Gemini) pela
 * credencial e cuida do provedor reserva.
 *
 * <p>A reserva só entra se o provedor anterior falhar <b>antes</b> de mostrar qualquer coisa — nenhum
 * trecho de texto, nenhuma ferramenta executada. Depois disso a falha sobe: não dá para desfazer o
 * que o usuário já viu nem repetir uma consulta como se fosse a primeira.</p>
 */
@Component
public class RoteadorLlmAdapter implements AssistenteLlmPort {

    private static final Logger log = LoggerFactory.getLogger(RoteadorLlmAdapter.class);

    private final Map<ProvedorIa, ClienteLlm> clientes = new EnumMap<>(ProvedorIa.class);

    RoteadorLlmAdapter(List<ClienteLlm> clientes) {
        for (ClienteLlm c : clientes) {
            if (this.clientes.putIfAbsent(c.provedor(), c) != null) {
                throw new IllegalStateException("Dois clientes de IA para " + c.provedor());
            }
        }
    }

    @Override
    public RespostaTurno conversar(Requisicao requisicao, Ouvinte ouvinte) {
        List<AssistantCredentials> cadeia = requisicao.cadeia();
        if (cadeia == null || cadeia.isEmpty()) {
            throw new IllegalArgumentException("Requisição sem credenciais");
        }
        AssistenteProvedorException ultima = null;
        for (int i = 0; i < cadeia.size(); i++) {
            AssistantCredentials credenciais = cadeia.get(i);
            Vigia vigia = new Vigia(ouvinte);
            try {
                return cliente(credenciais.provedor()).conversar(credenciais, requisicao, vigia).com(credenciais);
            } catch (AssistenteProvedorException ex) {
                ultima = ex;
                boolean temReserva = i + 1 < cadeia.size();
                if (vigia.emitiu || !temReserva) {
                    throw ex;
                }
                log.warn("assistente.llm.reserva de={} para={} motivo={}", credenciais.provedor(),
                        cadeia.get(i + 1).provedor(), ex.getMessage());
            }
        }
        throw ultima;
    }

    @Override
    public IntegrationTestResult testar(AssistantCredentials credenciais) {
        return cliente(credenciais.provedor()).testar(credenciais);
    }

    private ClienteLlm cliente(ProvedorIa provedor) {
        ClienteLlm c = clientes.get(provedor);
        if (c == null) {
            throw new IllegalStateException("Sem cliente de IA para " + provedor);
        }
        return c;
    }

    /** Repassa ao ouvinte do turno e anota se algo já chegou ao usuário. */
    private static final class Vigia implements Ouvinte {

        private final Ouvinte destino;
        private boolean emitiu;

        Vigia(Ouvinte destino) {
            this.destino = destino;
        }

        @Override
        public void texto(String delta) {
            emitiu = true;
            destino.texto(delta);
        }

        @Override
        public ResultadoFerramenta ferramenta(String nome, String inputJson) {
            emitiu = true;
            return destino.ferramenta(nome, inputJson);
        }

        @Override
        public boolean cancelado() {
            return destino.cancelado();
        }
    }
}
