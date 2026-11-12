package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.assistente.AcaoJaExecutadaException;
import com.cernecommerce.core.domain.exception.assistente.ConversaNaoEncontradaException;
import com.cernecommerce.core.domain.exception.assistente.MensagemNaoEncontradaException;
import com.cernecommerce.core.domain.exception.assistente.SubstituicaoInvalidaException;
import com.cernecommerce.core.domain.exception.assistente.TituloInvalidoException;
import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.Conversa;
import com.cernecommerce.core.domain.model.assistente.ConversaDetalhe;
import com.cernecommerce.core.domain.model.assistente.EstadoAcao;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.model.assistente.NotaFeedback;
import com.cernecommerce.core.domain.model.assistente.PapelMensagem;
import com.cernecommerce.core.domain.model.assistente.PerguntaRegistrada;
import com.cernecommerce.core.domain.model.assistente.ReservaAcao;
import com.cernecommerce.core.ports.in.AssistenteConversaUseCase;
import com.cernecommerce.core.ports.out.assistente.AssistenteRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Conversas do assistente e o ciclo das ações pendentes. Cada método é uma transação curta: o
 * stream do modelo roda fora daqui e nunca segura conexão do banco.
 */
public class AssistenteConversaService implements AssistenteConversaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AssistenteConversaService.class);

    static final int LIMITE_CONVERSAS = 50;
    static final int TITULO_MAX = 60;
    /** Título dado pelo usuário ao renomear. */
    static final int TITULO_RENOMEAR_MAX = 80;
    static final int MOTIVO_MAX = 500;
    /** EXECUTANDO por mais que isto é execução que caiu no meio (reinício, OOM). */
    static final Duration EXECUCAO_TRAVADA = Duration.ofMinutes(5);

    private final AssistenteRepositoryPort repository;
    private final Duration validadeAcao;
    private final Clock clock;

    public AssistenteConversaService(AssistenteRepositoryPort repository, Duration validadeAcao, Clock clock) {
        this.repository = repository;
        this.validadeAcao = validadeAcao;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Conversa> listar(String username) {
        return repository.listarConversas(username, LIMITE_CONVERSAS);
    }

    @Override
    @Transactional
    public Conversa criar(String username) {
        Instant agora = clock.instant();
        return repository.salvarConversa(new Conversa(UUID.randomUUID().toString(), username, null, agora, agora));
    }

    @Override
    @Transactional(readOnly = true)
    public ConversaDetalhe obter(String conversaId, String username) {
        Conversa conversa = exigir(conversaId, username);
        return new ConversaDetalhe(conversa, repository.listarMensagens(conversaId),
                repository.listarAcoes(conversaId));
    }

    @Override
    @Transactional
    public void excluir(String conversaId, String username) {
        exigir(conversaId, username);
        repository.excluirConversa(conversaId);
    }

    @Override
    @Transactional(readOnly = true)
    public Conversa exigir(String conversaId, String username) {
        return repository.buscarConversa(conversaId)
                .filter(c -> c.pertenceA(username))
                .orElseThrow(() -> new ConversaNaoEncontradaException("Conversa não encontrada"));
    }

    @Override
    @Transactional
    public PerguntaRegistrada registrarMensagemUsuario(String conversaId, String username, String texto) {
        Conversa conversa = exigir(conversaId, username);
        Instant agora = clock.instant();
        String tituloNovo = conversa.titulo() == null ? titulo(texto) : null;
        String titulo = tituloNovo != null ? tituloNovo : conversa.titulo();
        repository.salvarConversa(new Conversa(conversa.id(), conversa.username(), titulo, conversa.criadaEm(), agora));
        Mensagem pergunta = repository.salvarMensagem(new Mensagem(UUID.randomUUID().toString(), conversaId,
                PapelMensagem.USUARIO, texto, null, null, null, null, agora));
        return new PerguntaRegistrada(pergunta, tituloNovo);
    }

    @Override
    @Transactional
    public Conversa renomear(String conversaId, String username, String titulo) {
        Conversa conversa = exigir(conversaId, username);
        String limpo = titulo == null ? "" : titulo.strip().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) {
            throw new TituloInvalidoException("Informe um título.");
        }
        // A remoção de dado pessoal pode alongar o texto ("[CPF removido]"): corta em vez de recusar.
        if (limpo.length() > TITULO_RENOMEAR_MAX) {
            limpo = limpo.substring(0, TITULO_RENOMEAR_MAX).strip();
        }
        return repository.salvarConversa(new Conversa(conversa.id(), conversa.username(), limpo,
                conversa.criadaEm(), conversa.atualizadaEm()));
    }

    @Override
    @Transactional
    public Mensagem avaliar(String mensagemId, String username, NotaFeedback nota, String motivo) {
        Mensagem mensagem = repository.buscarMensagem(mensagemId)
                .filter(m -> m.papel() == PapelMensagem.ASSISTENTE)
                .filter(m -> repository.buscarConversa(m.conversaId()).map(c -> c.pertenceA(username)).orElse(false))
                .orElseThrow(MensagemNaoEncontradaException::new);
        if (nota == null) {
            repository.salvarFeedback(mensagemId, null, null, null);
            return comFeedback(mensagem, null, null, null);
        }
        String m = motivo == null ? null : motivo.strip();
        if (m != null && m.isEmpty()) {
            m = null;
        }
        if (m != null && m.length() > MOTIVO_MAX) {
            m = m.substring(0, MOTIVO_MAX).strip();
        }
        Instant agora = clock.instant();
        repository.salvarFeedback(mensagemId, nota, m, agora);
        return comFeedback(mensagem, nota, m, agora);
    }

    @Override
    @Transactional
    public void substituirDesde(String conversaId, String username, String perguntaId) {
        exigir(conversaId, username);
        List<Mensagem> mensagens = repository.listarMensagens(conversaId);
        int indice = -1;
        for (int i = mensagens.size() - 1; i >= 0; i--) {
            if (mensagens.get(i).papel() == PapelMensagem.USUARIO) {
                indice = i;
                break;
            }
        }
        if (indice < 0 || !mensagens.get(indice).id().equals(perguntaId)) {
            throw new SubstituicaoInvalidaException();
        }
        List<Mensagem> removidas = mensagens.subList(indice, mensagens.size());
        Set<String> ids = new HashSet<>();
        removidas.forEach(m -> ids.add(m.id()));
        List<AcaoPendente> acoes = repository.listarAcoes(conversaId).stream()
                .filter(a -> ids.contains(a.mensagemId()))
                .toList();
        if (acoes.stream().anyMatch(a -> a.estado() == EstadoAcao.EXECUTADA || a.estado() == EstadoAcao.EXECUTANDO)) {
            throw new AcaoJaExecutadaException();
        }
        // Um cartão antigo aberto em outra aba não pode mais ser confirmado. As linhas ficam (auditoria).
        acoes.stream()
                .filter(a -> a.estado() == EstadoAcao.PENDENTE)
                .forEach(a -> repository.salvarAcao(a.comEstado(EstadoAcao.EXPIRADA, null, null, null)));
        repository.excluirMensagens(removidas.stream().map(Mensagem::id).toList());
        log.info("assistente.substituir conversa={} mensagens={} acoesExpiradas={}", conversaId, removidas.size(),
                acoes.stream().filter(a -> a.estado() == EstadoAcao.PENDENTE).count());
    }

    private static Mensagem comFeedback(Mensagem m, NotaFeedback nota, String motivo, Instant em) {
        return new Mensagem(m.id(), m.conversaId(), m.papel(), m.texto(), m.blocosJson(), m.linksJson(),
                m.provedor(), m.modelo(), m.criadaEm(), nota, motivo, em);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mensagem> historico(String conversaId, int limite) {
        return repository.ultimasMensagens(conversaId, limite);
    }

    @Override
    @Transactional
    public Mensagem registrarRespostaAssistente(String mensagemId, String conversaId, String texto,
            String blocosJson, String linksJson, String provedor, String modelo) {
        Instant agora = clock.instant();
        repository.buscarConversa(conversaId).ifPresent(c -> repository.salvarConversa(
                new Conversa(c.id(), c.username(), c.titulo(), c.criadaEm(), agora)));
        return repository.salvarMensagem(new Mensagem(mensagemId, conversaId, PapelMensagem.ASSISTENTE,
                texto == null ? "" : texto, blocosJson, linksJson, provedor, modelo, agora));
    }

    @Override
    @Transactional
    public AcaoPendente proporAcao(String conversaId, String mensagemId, String username, String tipo,
            String parametrosJson, String titulo, String camposJson) {
        Instant agora = clock.instant();
        return repository.salvarAcao(new AcaoPendente(UUID.randomUUID().toString(), conversaId, mensagemId,
                username, tipo, parametrosJson, titulo, camposJson, EstadoAcao.PENDENTE, agora,
                agora.plus(validadeAcao), null, null, null));
    }

    @Override
    @Transactional
    public ReservaAcao reservarParaExecucao(String acaoId, String username) {
        AcaoPendente acao = acaoDoUsuario(acaoId, username, true);
        Instant agora = clock.instant();
        if (acao.vencida(agora)) {
            return new ReservaAcao(repository.salvarAcao(acao.comEstado(EstadoAcao.EXPIRADA, null, null, null)),
                    false);
        }
        if (acao.estado() != EstadoAcao.PENDENTE) {
            return new ReservaAcao(acao, false);
        }
        // executadaEm marca o início: é por ele que a retenção acha execução travada.
        return new ReservaAcao(repository.salvarAcao(acao.comEstado(EstadoAcao.EXECUTANDO, null, null, agora)), true);
    }

    @Override
    @Transactional
    public AcaoPendente concluirExecucao(String acaoId, EstadoAcao estado, String resultado, String linksJson) {
        AcaoPendente acao = repository.buscarAcaoParaAtualizar(acaoId)
                .orElseThrow(() -> new ConversaNaoEncontradaException("Ação não encontrada"));
        return repository.salvarAcao(acao.comEstado(estado, resultado, linksJson, clock.instant()));
    }

    @Override
    @Transactional
    public AcaoPendente recusar(String acaoId, String username) {
        AcaoPendente acao = acaoDoUsuario(acaoId, username, true);
        Instant agora = clock.instant();
        if (acao.vencida(agora)) {
            return repository.salvarAcao(acao.comEstado(EstadoAcao.EXPIRADA, null, null, null));
        }
        if (acao.estado() != EstadoAcao.PENDENTE) {
            return acao;
        }
        return repository.salvarAcao(acao.comEstado(EstadoAcao.RECUSADA, null, null, agora));
    }

    @Override
    @Transactional
    public long aplicarRetencao(int dias) {
        Instant agora = clock.instant();
        int expiradas = repository.expirarPendentes(agora);
        int travadas = repository.falharExecucoesTravadas(agora.minus(EXECUCAO_TRAVADA),
                "A execução foi interrompida. Confira se a alteração foi aplicada antes de pedir de novo.");
        long apagadas = repository.excluirConversasAntesDe(agora.minus(dias, ChronoUnit.DAYS));
        log.info("assistente.retencao conversasApagadas={} acoesExpiradas={} execucoesTravadas={}", apagadas,
                expiradas, travadas);
        return apagadas;
    }

    private AcaoPendente acaoDoUsuario(String acaoId, String username, boolean travar) {
        return (travar ? repository.buscarAcaoParaAtualizar(acaoId) : repository.buscarAcao(acaoId))
                .filter(a -> a.username().equals(username))
                .orElseThrow(() -> new ConversaNaoEncontradaException("Ação não encontrada"));
    }

    static String titulo(String texto) {
        String limpo = texto == null ? "" : texto.strip().replaceAll("\\s+", " ");
        if (limpo.isEmpty()) {
            return "Nova conversa";
        }
        return limpo.length() <= TITULO_MAX ? limpo : limpo.substring(0, TITULO_MAX - 1).strip() + "…";
    }
}
