package com.cernecommerce.core.ports.out.llm;

import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.domain.model.config.IntegrationTestResult;

import java.util.List;

/**
 * Provedor de IA do assistente (Claude, ChatGPT ou Gemini). O adaptador conduz o laço modelo →
 * ferramentas → modelo de um turno e chama o {@link Ouvinte} para cada trecho de texto e cada
 * ferramenta pedida. Quem executa as ferramentas é o ouvinte, como o usuário logado; o adaptador só
 * repassa o resultado.
 *
 * <p>A requisição traz uma cadeia de credenciais: o provedor ativo e, se houver, o de reserva. A
 * reserva só entra se o ativo falhar antes de qualquer texto ou ferramenta.</p>
 *
 * <p>Nada aqui depende do SDK: o JSON das ferramentas trafega como texto.</p>
 */
public interface AssistenteLlmPort {

    /**
     * Executa um turno até o modelo terminar, o limite de iterações ou o cancelamento.
     *
     * @throws com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException
     *         quando o provedor falha
     */
    RespostaTurno conversar(Requisicao requisicao, Ouvinte ouvinte);

    /** Chamada mínima para validar chave e modelo (botão "Testar" da integração). */
    IntegrationTestResult testar(AssistantCredentials credenciais);

    /** Uma ferramenta oferecida ao modelo. {@code schemaJson} é um JSON Schema de objeto. */
    record FerramentaDef(String nome, String descricao, String schemaJson) { }

    /** Mensagem anterior da conversa, já sem dados pessoais. */
    record MensagemHistorico(boolean doUsuario, String texto) { }

    record Requisicao(
            /* O ativo primeiro; depois a reserva, se cadastrada. Nunca vazia. */
            List<AssistantCredentials> cadeia,
            String systemPrompt,
            List<FerramentaDef> ferramentas,
            List<MensagemHistorico> historico,
            /* Mensagem nova do usuário, já sanitizada e com o contexto da tela. */
            String mensagemUsuario,
            long maxTokens,
            int maxIteracoes) { }

    /** Resultado de uma ferramenta devolvido ao modelo. */
    record ResultadoFerramenta(String conteudo, boolean erro) {
        public static ResultadoFerramenta ok(String conteudo) {
            return new ResultadoFerramenta(conteudo, false);
        }

        public static ResultadoFerramenta falha(String mensagem) {
            return new ResultadoFerramenta(mensagem, true);
        }
    }

    /**
     * {@code motivo}: {@code CONCLUIDO}, {@code LIMITE_ITERACOES}, {@code LIMITE_TOKENS},
     * {@code RECUSA} ou {@code CANCELADO}. {@code provedor}/{@code modelo}: quem respondeu.
     */
    record RespostaTurno(String motivo, String provedor, String modelo) {

        public RespostaTurno(String motivo) {
            this(motivo, null, null);
        }

        public RespostaTurno com(AssistantCredentials quem) {
            return new RespostaTurno(motivo, quem.provedor().name(), quem.model());
        }
    }

    interface Ouvinte {
        void texto(String delta);

        ResultadoFerramenta ferramenta(String nome, String inputJson);

        /**
         * O turno foi cancelado ("Parar" explícito ou prazo do turno): o adaptador para no próximo
         * ponto seguro. Queda da conexão do navegador não cancela.
         */
        boolean cancelado();

        /**
         * Tokens de uma chamada ao provedor (uma por iteração do laço). {@code entrada} sem o que veio
         * do cache; {@code cache} é a entrada lida do cache; {@code saida} inclui o raciocínio.
         */
        default void uso(long entrada, long saida, long cache) { }
    }
}
