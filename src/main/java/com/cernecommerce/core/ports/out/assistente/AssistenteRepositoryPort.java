package com.cernecommerce.core.ports.out.assistente;

import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.Conversa;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Conversas, mensagens e ações do assistente de IA. */
public interface AssistenteRepositoryPort {

    Conversa salvarConversa(Conversa conversa);

    Optional<Conversa> buscarConversa(String id);

    /** Do usuário, mais recente primeiro. */
    List<Conversa> listarConversas(String username, int limite);

    void excluirConversa(String id);

    Mensagem salvarMensagem(Mensagem mensagem);

    Optional<Mensagem> buscarMensagem(String id);

    /** Grava (ou limpa, com tudo {@code null}) a avaliação de uma mensagem. Não mexe na conversa. */
    void salvarFeedback(String mensagemId, NotaFeedback nota, String motivo, Instant em);

    /** Apaga só estas mensagens. As ações ligadas a elas ficam (auditoria). */
    void excluirMensagens(List<String> ids);

    /** Em ordem de criação. */
    List<Mensagem> listarMensagens(String conversaId);

    /** As {@code limite} mais recentes, em ordem de criação. */
    List<Mensagem> ultimasMensagens(String conversaId, int limite);

    AcaoPendente salvarAcao(AcaoPendente acao);

    Optional<AcaoPendente> buscarAcao(String id);

    /** Com trava de linha — só dentro de transação. */
    Optional<AcaoPendente> buscarAcaoParaAtualizar(String id);

    List<AcaoPendente> listarAcoes(String conversaId);

    /** Retenção: apaga conversas (e, em cascata, mensagens e ações) sem atividade desde {@code corte}. */
    long excluirConversasAntesDe(Instant corte);

    /** PENDENTE vencida vira EXPIRADA. */
    int expirarPendentes(Instant agora);

    /** EXECUTANDO há mais de {@code corte} vira FALHOU — a execução caiu no meio. */
    int falharExecucoesTravadas(Instant corte, String resultado);
}
