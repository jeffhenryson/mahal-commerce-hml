package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.assistente.FerramentaSupport.EntradaInvalida;
import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;
import com.cernecommerce.core.domain.exception.assistente.AssistenteNaoConfiguradoException;
import com.cernecommerce.core.domain.exception.assistente.AssistenteOcupadoException;
import com.cernecommerce.core.domain.exception.assistente.AssistenteProvedorException;
import com.cernecommerce.core.domain.model.assistente.AcaoPendente;
import com.cernecommerce.core.domain.model.assistente.Mensagem;
import com.cernecommerce.core.domain.model.assistente.PapelMensagem;
import com.cernecommerce.core.domain.model.assistente.PerguntaRegistrada;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.in.AssistenteConversaUseCase;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.FerramentaDef;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.MensagemHistorico;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Requisicao;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.RespostaTurno;
import com.cernecommerce.infra.audit.AuditOrigemContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

/**
 * Conduz um turno do assistente: valida tudo o que pode falhar ainda no request (para responder
 * HTTP comum, não 401 pelo re-dispatch assíncrono), abre o {@link SseEmitter} e roda o laço do
 * modelo no executor próprio, com o {@code SecurityContext} do usuário — as ferramentas chamam os
 * controllers e passam pelo {@code @PreAuthorize} deles.
 *
 * <p>Nenhuma transação fica aberta durante o stream: cada gravação e cada ferramenta é uma
 * transação curta.</p>
 */
@Component
public class AssistenteOrquestrador {

    private static final Logger log = LoggerFactory.getLogger(AssistenteOrquestrador.class);

    private final AssistenteConversaUseCase conversas;
    private final AssistantIntegrationUseCase integracao;
    private final AssistenteLlmPort llm;
    private final TaskExecutor executor;
    private final Map<String, Ferramenta> ferramentas = new LinkedHashMap<>();
    private final List<FerramentaDef> definicoes;
    private final Map<String, Boolean> emAndamento = new ConcurrentHashMap<>();
    private final Clock clock;

    private final Duration sseTimeout;
    private final long maxTokens;
    private final int maxIteracoes;
    private final int historicoMax;

    public AssistenteOrquestrador(AssistenteConversaUseCase conversas, AssistantIntegrationUseCase integracao,
            AssistenteLlmPort llm, @Qualifier("assistenteExecutor") TaskExecutor executor,
            List<ProvedorFerramentas> provedores,
            @Value("${assistente.sse-timeout:PT5M}") Duration sseTimeout,
            @Value("${assistente.max-tokens:8000}") long maxTokens,
            @Value("${assistente.max-iteracoes:8}") int maxIteracoes,
            @Value("${assistente.historico-max-mensagens:20}") int historicoMax) {
        this.conversas = conversas;
        this.integracao = integracao;
        this.llm = llm;
        this.executor = executor;
        this.clock = Clock.systemUTC();
        this.sseTimeout = sseTimeout;
        this.maxTokens = maxTokens;
        this.maxIteracoes = maxIteracoes;
        this.historicoMax = historicoMax;
        for (ProvedorFerramentas p : provedores) {
            for (Ferramenta f : p.ferramentas()) {
                if (ferramentas.putIfAbsent(f.nome(), f) != null) {
                    throw new IllegalStateException("Ferramenta duplicada no assistente: " + f.nome());
                }
            }
        }
        this.definicoes = ferramentas.values().stream()
                .map(f -> new FerramentaDef(f.nome(), f.descricao(), f.schemaJson()))
                .toList();
    }

    /** Nomes das ferramentas oferecidas ao modelo (diagnóstico e testes). */
    public List<String> nomesFerramentas() {
        return List.copyOf(ferramentas.keySet());
    }

    /**
     * Valida e abre o stream. Tudo que lança aqui vira HTTP comum (404, 409); depois do emitter, os
     * erros só saem como evento {@code erro}.
     */
    public SseEmitter iniciarTurno(String conversaId, String username, String textoBruto, String rota,
            String tituloTela, String substituirDesde, String ip, String userAgent) {
        // O provedor ativo e, se houver, a reserva — em ordem de uso.
        List<AssistantCredentials> credenciais = integracao.activeCredentials()
                .orElseThrow(AssistenteNaoConfiguradoException::new);
        conversas.exigir(conversaId, username);
        if (emAndamento.putIfAbsent(conversaId, Boolean.TRUE) != null) {
            throw new AssistenteOcupadoException("Já há uma resposta em andamento nesta conversa.");
        }
        if (substituirDesde != null && !substituirDesde.isBlank()) {
            // Ainda no request: os 409 saem como HTTP. Com a conversa travada, ninguém grava no meio.
            try {
                conversas.substituirDesde(conversaId, username, substituirDesde.strip());
            } catch (RuntimeException ex) {
                emAndamento.remove(conversaId);
                throw ex;
            }
        }
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        SseAssistenteWriter writer = new SseAssistenteWriter(emitter);
        SecurityContext contexto = SecurityContextHolder.getContext();
        Authentication autenticacao = contexto.getAuthentication();
        AuditOrigemContext.Origem origem = new AuditOrigemContext.Origem(AuditOrigemContext.ASSISTENTE, ip, userAgent);
        String texto = PiiSanitizer.limpar(textoBruto.strip());
        try {
            executor.execute(() -> comUsuario(autenticacao, () -> executarTurno(conversaId, username, texto, rota,
                    tituloTela, credenciais, origem, writer)));
        } catch (RejectedExecutionException ex) {
            emAndamento.remove(conversaId);
            throw new AssistenteOcupadoException("O assistente está ocupado agora. Tente de novo em instantes.");
        }
        return emitter;
    }

    private void executarTurno(String conversaId, String username, String texto, String rota, String tituloTela,
            List<AssistantCredentials> credenciais, AuditOrigemContext.Origem origem, SseAssistenteWriter writer) {
        String mensagemId = UUID.randomUUID().toString();
        Turno turno = new Turno(conversaId, mensagemId, username, origem, writer);
        boolean respostaGravada = false;
        String perguntaId = null;
        try {
            List<MensagemHistorico> historico = conversas.historico(conversaId, historicoMax).stream()
                    .map(AssistenteOrquestrador::historico)
                    .toList();
            PerguntaRegistrada pergunta = conversas.registrarMensagemUsuario(conversaId, username, texto);
            perguntaId = pergunta.mensagem().id();
            if (pergunta.tituloNovo() != null) {
                writer.titulo(pergunta.tituloNovo());
            }

            RespostaTurno resposta = llm.conversar(new Requisicao(credenciais, AssistentePrompt.SISTEMA, definicoes,
                    historico, comContexto(texto, rota, tituloTela), maxTokens, maxIteracoes), turno);
            turno.provedor = resposta.provedor();
            turno.modelo = resposta.modelo();
            switch (resposta.motivo()) {
                case "RECUSA" -> turno.texto(separar(turno) + "Não posso ajudar com esse pedido.");
                case "LIMITE_ITERACOES", "LIMITE_TOKENS" -> turno.texto(separar(turno)
                        + "(Parei aqui: a resposta ficou longa demais. Peça o restante, se precisar.)");
                default -> { }
            }
            Mensagem salva = gravarResposta(turno);
            respostaGravada = true;
            // Libera antes do fim: quem recebe o evento pode mandar a próxima (ou refazer) na hora.
            emAndamento.remove(conversaId);
            writer.fim(salva.id(), perguntaId, turno.provedor, turno.modelo);
        } catch (AssistenteProvedorException ex) {
            log.warn("assistente.turno.provedor conversa={} erro={}", conversaId, ex.getMessage());
            respostaGravada = gravarParcial(turno, respostaGravada);
            emAndamento.remove(conversaId);
            writer.erro("PROVEDOR", ex.getMessage(), perguntaId);
        } catch (RuntimeException ex) {
            log.error("assistente.turno.falha conversa={}", conversaId, ex);
            respostaGravada = gravarParcial(turno, respostaGravada);
            emAndamento.remove(conversaId);
            writer.erro("ERRO_INTERNO", "Não foi possível concluir a resposta. Tente de novo.", perguntaId);
        } finally {
            emAndamento.remove(conversaId);
            writer.concluir();
        }
    }

    private Mensagem gravarResposta(Turno turno) {
        return conversas.registrarRespostaAssistente(turno.mensagemId, turno.conversaId,
                PiiSanitizer.limpar(turno.texto.toString()), AssistenteMapper.json(turno.blocos),
                AssistenteMapper.json(turno.links), turno.provedor, turno.modelo);
    }

    /** O que já foi mostrado (texto parcial, tabelas, ações propostas) fica no histórico. */
    private boolean gravarParcial(Turno turno, boolean jaGravada) {
        if (jaGravada || (turno.texto.isEmpty() && turno.blocos.isEmpty() && !turno.propos)) {
            return jaGravada;
        }
        try {
            gravarResposta(turno);
            return true;
        } catch (RuntimeException ex) {
            log.warn("assistente.turno.parcial.falhou conversa={} erro={}", turno.conversaId, ex.getMessage());
            return false;
        }
    }

    private static String separar(Turno turno) {
        return turno.texto.isEmpty() ? "" : "\n\n";
    }

    private String comContexto(String texto, String rota, String tituloTela) {
        LocalDate hoje = LocalDate.now(clock.withZone(FerramentaSupport.FUSO));
        StringBuilder sb = new StringBuilder("<contexto_tela>\nHoje: ")
                .append(hoje).append(" (").append(FerramentaSupport.data(hoje)).append(")\n");
        String r = rotaLimpa(rota);
        if (r != null) {
            sb.append("Tela: ").append(r);
            String t = tituloTela == null ? null : PiiSanitizer.limpar(tituloTela.strip());
            if (t != null && !t.isEmpty()) {
                sb.append(" — ").append(t.length() > 120 ? t.substring(0, 120) : t);
            }
            sb.append('\n');
        }
        return sb.append("</contexto_tela>\n\n").append(texto).toString();
    }

    /** Só o path, sem query string (pode carregar filtros com dado pessoal). */
    static String rotaLimpa(String rota) {
        if (rota == null || rota.isBlank()) {
            return null;
        }
        String path = rota.strip().split("[?#]", 2)[0];
        return path.startsWith("/") && path.length() <= 200 && path.matches("[A-Za-z0-9/_\\-]+") ? path : null;
    }

    private static MensagemHistorico historico(Mensagem m) {
        return new MensagemHistorico(m.papel() == PapelMensagem.USUARIO, m.texto().isBlank() ? "(sem texto)" : m.texto());
    }

    private static void comUsuario(Authentication autenticacao, Runnable tarefa) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        try {
            tarefa.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /** Estado de um turno e ponte entre o adaptador do modelo e as ferramentas. */
    private final class Turno implements AssistenteLlmPort.Ouvinte {

        private final String conversaId;
        private final String mensagemId;
        private final String username;
        private final AuditOrigemContext.Origem origem;
        private final SseAssistenteWriter writer;
        private final StringBuilder texto = new StringBuilder();
        private final List<AssistenteBlocoDTO> blocos = new ArrayList<>();
        private final List<AssistenteLinkDTO> links = new ArrayList<>();
        private boolean propos;
        /** Quem respondeu — só conhecido no fim do turno (a reserva pode ter entrado). */
        private String provedor;
        private String modelo;

        Turno(String conversaId, String mensagemId, String username, AuditOrigemContext.Origem origem,
                SseAssistenteWriter writer) {
            this.conversaId = conversaId;
            this.mensagemId = mensagemId;
            this.username = username;
            this.origem = origem;
            this.writer = writer;
        }

        @Override
        public void texto(String delta) {
            texto.append(delta);
            writer.texto(delta);
        }

        @Override
        public boolean cancelado() {
            return writer.fechado();
        }

        @Override
        public AssistenteLlmPort.ResultadoFerramenta ferramenta(String nome, String inputJson) {
            Ferramenta f = ferramentas.get(nome);
            if (f == null) {
                return AssistenteLlmPort.ResultadoFerramenta.falha("Ferramenta desconhecida: " + nome);
            }
            writer.ferramenta(f.rotulo(), true);
            try {
                ResultadoFerramenta r = executar(f, inputJson);
                return publicar(r);
            } finally {
                writer.ferramenta(f.rotulo(), false);
            }
        }

        private ResultadoFerramenta executar(Ferramenta f, String inputJson) {
            JsonNode input;
            try {
                input = inputJson == null || inputJson.isBlank() ? null : FerramentaSupport.MAPPER.readTree(inputJson);
            } catch (Exception ex) {
                input = null;
            }
            if (input == null || !input.isObject()) {
                return ResultadoFerramenta.falha("Entrada inválida: envie um objeto JSON conforme o schema.");
            }
            JsonNode entrada = input;
            try {
                return AuditOrigemContext.executar(origem, () -> f.executar().apply(entrada));
            } catch (EntradaInvalida ex) {
                return ResultadoFerramenta.falha(ex.getMessage());
            } catch (AccessDeniedException ex) {
                return ResultadoFerramenta.falha("O usuário não tem permissão para esta consulta.");
            } catch (RuntimeException ex) {
                log.info("assistente.ferramenta.erro nome={} tipo={} erro={}", f.nome(), ex.getClass().getSimpleName(),
                        ex.getMessage());
                String msg = ex.getMessage() == null ? "erro inesperado" : PiiSanitizer.limpar(ex.getMessage());
                return ResultadoFerramenta.falha("Não foi possível concluir: " + msg);
            }
        }

        private AssistenteLlmPort.ResultadoFerramenta publicar(ResultadoFerramenta r) {
            if (r.acao() != null) {
                AcaoProposta p = r.acao();
                String campos = AssistenteMapper.json(p.campos());
                AcaoPendente acao = conversas.proporAcao(conversaId, mensagemId, username, p.tipo(),
                        p.parametrosJson(), p.titulo(), campos == null ? "[]" : campos);
                propos = true;
                AssistenteAcaoDTO dto = AssistenteMapper.acao(acao, clock.instant());
                writer.acao(dto);
                return AssistenteLlmPort.ResultadoFerramenta.ok(FerramentaSupport.json(Map.of(
                        "acaoId", acao.id(),
                        "estado", "PENDENTE",
                        "observacao", "Cartão de confirmação mostrado ao usuário. Nada foi executado ainda.")));
            }
            for (AssistenteBlocoDTO bloco : r.blocos()) {
                blocos.add(bloco);
                writer.bloco(bloco);
            }
            for (AssistenteLinkDTO link : r.links()) {
                if (AssistenteMapper.rotaSegura(link.rota())) {
                    links.add(link);
                    writer.link(link);
                }
            }
            return r.erro()
                    ? AssistenteLlmPort.ResultadoFerramenta.falha(r.paraModelo())
                    : AssistenteLlmPort.ResultadoFerramenta.ok(r.paraModelo());
        }
    }
}
