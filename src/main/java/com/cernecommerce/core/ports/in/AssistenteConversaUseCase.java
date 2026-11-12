package com.cernecommerce.core.ports.in;

import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.Conversa;
import com.cernecommerce.core.domain.model.assistente.ConversaDetalhe;
import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.PerguntaRegistrada;
import com.cernecommerce.core.domain.model.assistente.ReservaAcao;

import java.util.List;

/**
 * Persistência das conversas do assistente e o ciclo das ações pendentes. Conversa ou ação de
 * outro usuário responde como inexistente ({@code ConversaNaoEncontradaException}).
 *
 * <p>Os textos chegam aqui já sem dados pessoais: quem remove é o adaptador de entrada, antes de
 * gravar e antes de enviar ao modelo.</p>
 */
public interface AssistenteConversaUseCase {

    List<Conversa> listar(String username);

    Conversa criar(String username);

    ConversaDetalhe obter(String conversaId, String username);

    void excluir(String conversaId, String username);

    /** A conversa, se for do usuário. */
    Conversa exigir(String conversaId, String username);

    /**
     * Grava a mensagem do usuário, dá título à conversa na primeira e atualiza {@code atualizadaEm}.
     * {@code tituloNovo} vem preenchido só quando esta mensagem deu o título.
     */
    PerguntaRegistrada registrarMensagemUsuario(String conversaId, String username, String texto);

    /**
     * Renomeia a conversa sem mexer em {@code atualizadaEm} (não sobe no histórico). Espaços são
     * normalizados; vazio lança {@code TituloInvalidoException}.
     */
    Conversa renomear(String conversaId, String username, String titulo);

    /**
     * Avalia uma resposta do assistente; {@code nota == null} limpa a avaliação. Mensagem do usuário,
     * inexistente ou de outra conversa: {@code MensagemNaoEncontradaException}. Devolve a mensagem
     * avaliada.
     */
    Mensagem avaliar(String mensagemId, String username, NotaFeedback nota, String motivo);

    /**
     * Prepara "gerar de novo"/"editar e reenviar": apaga {@code perguntaId} (a última pergunta) e tudo
     * depois dela. Ações PENDENTE dessas mensagens viram EXPIRADA; se alguma já foi executada (ou está
     * executando), lança {@code AcaoJaExecutadaException} sem apagar nada. Pergunta que não é a última:
     * {@code SubstituicaoInvalidaException}.
     */
    void substituirDesde(String conversaId, String username, String perguntaId);

    /** As últimas mensagens antes da atual, para o histórico enviado ao modelo. */
    List<Mensagem> historico(String conversaId, int limite);

    /** Grava a resposta do assistente com o id gerado no início do turno e quem respondeu. */
    Mensagem registrarRespostaAssistente(String mensagemId, String conversaId, String texto, String blocosJson,
            String linksJson, String provedor, String modelo);

    /** Guarda a escrita proposta, PENDENTE, com validade curta. */
    AcaoPendente proporAcao(String conversaId, String mensagemId, String username, String tipo,
            String parametrosJson, String titulo, String camposJson);

    /**
     * Tira a ação de PENDENTE para EXECUTANDO, com trava de linha. Vencida vira EXPIRADA; em outro
     * estado devolve como está, sem reservar.
     */
    ReservaAcao reservarParaExecucao(String acaoId, String username);

    /** Grava o desfecho de uma ação reservada (EXECUTADA ou FALHOU). */
    AcaoPendente concluirExecucao(String acaoId, EstadoAcao estado, String resultado, String linksJson);

    /** PENDENTE vira RECUSADA; outro estado devolve como está. */
    AcaoPendente recusar(String acaoId, String username);

    /** Retenção: apaga conversas paradas há mais de {@code dias} e expira ações vencidas. */
    long aplicarRetencao(int dias);
}
