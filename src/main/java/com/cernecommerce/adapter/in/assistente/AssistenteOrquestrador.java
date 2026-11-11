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
import com.cernecommerce.core.domain.model.assistente.UsoTokens;
import com.cernecommerce.core.domain.model.config.AssistantCredentials;
import com.cernecommerce.core.ports.in.AssistantIntegrationUseCase;
import com.cernecommerce.core.ports.in.AssistenteConversaUseCase;
import com.cernecommerce.core.ports.in.AssistenteUsoUseCase;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.FerramentaDef;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.MensagemHistorico;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.Requisicao;
import com.cernecommerce.core.ports.out.llm.AssistenteLlmPort.RespostaTurno;
import com.cernecommerce.infra.audit.AuditOrigemContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conduz um turno do assistente: valida tudo o que pode falhar ainda no request (para responder
 * HTTP comum, não 401 pelo re-dispatch assíncrono), abre o {@link SseEmitter} e roda o laço do
 * modelo no executor próprio, com o {@code SecurityContext} do usuário — as ferramentas chamam os
 * controllers e passam pelo {@code @PreAuthorize} deles.
 *
 * <p>Nenhuma transação fica aberta durante o stream: cada gravação e cada ferramenta é uma
 * transação curta.</p>
 *
 * <p>Queda da conexão não cancela o turno: ele termina e grava a resposta, que o front relê. Só o
 * "Parar" ({@link #cancelar}) ou o prazo {@code assistente.turno.timeout} interrompem.</p>
 */
@Component
public class AssistenteOrquestrador {

    private static final Logger log = LoggerFactory.getLogger(AssistenteOrquestrador.class);

    private final AssistenteConversaUseCase conversas;
    private final AssistantIntegrationUseCase integracao;
    private final AssistenteLlmPort llm;
    private final AssistenteUsoUseCase uso;
    private final MeterRegistry metricas;
    private final Counter quedas;
    private final TaskExecutor executor;
    private final Map<String, Ferramenta> ferramentas = new LinkedHashMap<>();
    private final List<FerramentaDef> definicoes;
    private final Map<String, TurnoAtivo> emAndamento = new ConcurrentHashMap<>();
    private final Clock clock;
    /** Heartbeat de todos os streams abertos: uma thread só, que apenas escreve um comentário. */
    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "assistente-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private final Duration sseTimeout;
    private final Duration turnoTimeout;
    private final Duration heartbeat;
    private final long maxTokens;
    private final int maxIteracoes;
    private final int historicoMax;

    public AssistenteOrquestrador(AssistenteConversaUseCase conversas, AssistantIntegrationUseCase integracao,
            AssistenteLlmPort llm, AssistenteUsoUseCase uso, MeterRegistry metricas,
            @Qualifier("assistenteExecutor") TaskExecutor executor,
            List<ProvedorFerramentas> provedores,
            @Value("${assistente.sse-timeout:PT5M}") Duration sseTimeout,
            @Value("${assistente.max-tokens:8000}") long maxTokens,
            @Value("${assistente.max-iteracoes:8}") int maxIteracoes,
            @Value("${assistente.historico-max-mensagens:20}") int historicoMax,
            @Value("${assistente.turno.timeout:PT4M}") Duration turnoTimeout,
            @Value("${assistente.sse.heartbeat:PT15S}") Duration heartbeat) {
        this.conversas = conversas;
        this.integracao = integracao;
        this.llm = llm;
        this.uso = uso;
        this.metricas = metricas;
        this.quedas = Counter.builder("assistente.sse.queda")
                .description("Streams do assistente que caíram antes do fim (o turno continua e grava)")
                .register(metricas);
        this.executor = executor;
        this.clock = Clock.systemUTC();
        this.sseTimeout = sseTimeout;
        this.maxTokens = maxTokens;
        this.maxIteracoes = maxIteracoes;
        this.historicoMax = historicoMax;
        this.turnoTimeout = turnoTimeout;
        this.heartbeat = heartbeat;
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

    @PreDestroy
    void encerrar() {
        agendador.shutdownNow();
    }

    /** Há um turno rodando nesta conversa (o front decide se espera a resposta gravada). */
    public boolean emAndamento(String conversaId) {
        return emAndamento.containsKey(conversaId);
    }

    /**
     * "Parar" explícito: o turno para no próximo ponto seguro e grava o que já tinha. Idempotente;
     * sem turno em andamento, não faz nada. Conversa de outro usuário: 404.
     */
    public void cancelar(String conversaId, String username) {
        conversas.exigir(conversaId, username);
        TurnoAtivo ativo = emAndamento.get(conversaId);
        if (ativo != null && ativo.username.equals(username) && ativo.cancelado.compareAndSet(false, true)) {
            log.info("assistente.turno.cancelado turnoId={} conversa={}", ativo.turnoId, conversaId);
        }
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
        uso.exigirDentroDoLimite();
        TurnoAtivo ativo = new TurnoAtivo(UUID.randomUUID().toString(), username,
                clock.instant().plus(turnoTimeout));
        if (emAndamento.putIfAbsent(conversaId, ativo) != null) {
            throw new AssistenteOcupadoException("Já há uma resposta em andamento nesta conversa.");
        }
        if (substituirDesde != null && !substituirDesde.isBlank()) {
            // Ainda no request: os 409 saem como HTTP. Com a conversa travada, ninguém grava no meio.
            try {
                conversas.substituirDesde(conversaId, username, substituirDesde.strip());
            } catch (RuntimeException ex) {
                emAndamento.remove(conversaId, ativo);
                throw ex;
            }
        }
        SseEmitter emitter = new SseEmitter(sseTimeout.toMillis());
        SseAssistenteWriter writer = new SseAssistenteWriter(emitter, ativo.turnoId, agendador, heartbeat, quedas::increment);
        SecurityContext contexto = SecurityContextHolder.getContext();
        Authentication autenticacao = contexto.getAuthentication();
        AuditOrigemContext.Origem origem = new AuditOrigemContext.Origem(AuditOrigemContext.ASSISTENTE, ip, userAgent);
        String texto = PiiSanitizer.limpar(textoBruto.strip());
        try {
            executor.execute(() -> comUsuario(autenticacao, ativo.turnoId, () -> executarTurno(conversaId, username,
                    texto, rota, tituloTela, credenciais, origem, ativo, writer)));
        } catch (RejectedExecutionException ex) {
            emAndamento.remove(conversaId, ativo);
            writer.concluir();
            throw new AssistenteOcupadoException("O assistente está ocupado agora. Tente de novo em instantes.");
        }
        return emitter;
    }

    private void executarTurno(String conversaId, String username, String texto, String rota, String tituloTela,
            List<AssistantCredentials> credenciais, AuditOrigemContext.Origem origem, TurnoAtivo ativo,
            SseAssistenteWriter writer) {
        String mensagemId = UUID.randomUUID().toString();
        Turno turno = new Turno(conversaId, mensagemId, username, origem, ativo, writer);
        boolean respostaGravada = false;
        String perguntaId = null;
        Timer.Sample duracao = Timer.start(metricas);
        String resultado = "erro";
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
                case "CANCELADO" -> {
                    resultado = ativo.cancelado.get() ? "cancelado" : "prazo";
                    if (!ativo.cancelado.get()) {
                        log.warn("assistente.turno.prazo turnoId={} conversa={} ms={}", ativo.turnoId, conversaId,
                                writer.ms());
                        turno.texto(separar(turno) + "(Parei aqui: a resposta demorou demais. Tente de novo.)");
                    }
                }
                default -> { }
            }
            Mensagem salva = gravarResposta(turno);
            respostaGravada = true;
            if ("erro".equals(resultado)) {
                resultado = "ok";
            }
            // Tabelas e links depois do texto: a explicação vem primeiro na tela.
            turno.descarregar();
            // Libera antes do fim: quem recebe o evento pode mandar a próxima (ou refazer) na hora.
            emAndamento.remove(conversaId, ativo);
            writer.fim(salva.id(), perguntaId, turno.provedor, turno.modelo);
            log.info("assistente.turno.ok provedor={} motivo={} ms={} clienteConectado={}", turno.provedor,
                    resposta.motivo(), writer.ms(), !writer.fechado());
        } catch (AssistenteProvedorException ex) {
            log.warn("assistente.turno.provedor conversa={} erro={}", conversaId, ex.getMessage());
            resultado = "provedor";
            respostaGravada = gravarParcial(turno, respostaGravada);
            turno.descarregar();
            emAndamento.remove(conversaId, ativo);
            writer.erro("PROVEDOR", ex.getMessage(), perguntaId);
        } catch (RuntimeException ex) {
            log.error("assistente.turno.falha conversa={}", conversaId, ex);
            respostaGravada = gravarParcial(turno, respostaGravada);
            turno.descarregar();
            emAndamento.remove(conversaId, ativo);
            writer.erro("ERRO_INTERNO", "Não foi possível concluir a resposta. Tente de novo.", perguntaId);
        } catch (Error ex) {
            // OutOfMemory, StackOverflow…: o stream ainda precisa de um fim legível.
            log.error("assistente.turno.falha.grave conversa={}", conversaId, ex);
            emAndamento.remove(conversaId, ativo);
            writer.erro("ERRO_INTERNO", "Não foi possível concluir a resposta. Tente de novo.", perguntaId);
            throw ex;
        } finally {
            emAndamento.remove(conversaId, ativo);
            writer.concluir();
            duracao.stop(Timer.builder("assistente.turno.duracao")
                    .description("Duração de um turno do assistente, do início do laço até o fim do stream")
                    .tag("provedor", turno.provedor == null ? "desconhecido" : turno.provedor)
                    .tag("resultado", resultado)
                    .register(metricas));
        }
    }

    private Mensagem gravarResposta(Turno turno) {
        Mensagem salva = conversas.registrarRespostaAssistente(turno.mensagemId, turno.conversaId,
                PiiSanitizer.limpar(turno.texto.toString()), AssistenteMapper.json(turno.blocos),
                AssistenteMapper.json(turno.links), turno.provedor, turno.modelo);
        try {
            uso.registrar(salva.id(), turno.uso);
        } catch (RuntimeException ex) {
            // O relatório de uso não pode derrubar a resposta já gravada.
            log.warn("assistente.uso.falhou mensagem={} erro={}", salva.id(), ex.getMessage());
        }
        return salva;
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

    private static void comUsuario(Authentication autenticacao, String turnoId, Runnable tarefa) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        MDC.put("turnoId", turnoId);
        try {
            tarefa.run();
        } finally {
            MDC.remove("turnoId");
            SecurityContextHolder.clearContext();
        }
    }

    /** Turno registrado na conversa: quem o abriu, o "Parar" e o prazo para um turno órfão. */
    private static final class TurnoAtivo {
        private final String turnoId;
        private final String username;
        private final Instant prazo;
        private final AtomicBoolean cancelado = new AtomicBoolean();

        TurnoAtivo(String turnoId, String username, Instant prazo) {
            this.turnoId = turnoId;
            this.username = username;
            this.prazo = prazo;
        }
    }

    /** Estado de um turno e ponte entre o adaptador do modelo e as ferramentas. */
    private final class Turno implements AssistenteLlmPort.Ouvinte {

        private final String conversaId;
        private final String mensagemId;
        private final String username;
        private final AuditOrigemContext.Origem origem;
        private final TurnoAtivo ativo;
        private final SseAssistenteWriter writer;
        private final StringBuilder texto = new StringBuilder();
        private final List<AssistenteBlocoDTO> blocos = new ArrayList<>();
        private final List<AssistenteLinkDTO> links = new ArrayList<>();
        /** Ainda não enviados: saem depois do último texto, antes do fim. */
        private final List<AssistenteBlocoDTO> blocosPendentes = new ArrayList<>();
        private final List<AssistenteLinkDTO> linksPendentes = new ArrayList<>();
        private int etapas;
        private UsoTokens uso = UsoTokens.ZERO;
        private boolean propos;
        /** Quem respondeu — só conhecido no fim do turno (a reserva pode ter entrado). */
        private String provedor;
        private String modelo;

        Turno(String conversaId, String mensagemId, String username, AuditOrigemContext.Origem origem,
                TurnoAtivo ativo, SseAssistenteWriter writer) {
            this.conversaId = conversaId;
            this.mensagemId = mensagemId;
            this.username = username;
            this.origem = origem;
            this.ativo = ativo;
            this.writer = writer;
        }

        void descarregar() {
            blocosPendentes.forEach(writer::bloco);
            blocosPendentes.clear();
            linksPendentes.forEach(writer::link);
            linksPendentes.clear();
        }

        @Override
        public void texto(String delta) {
            texto.append(delta);
            writer.texto(delta);
        }

        @Override
        public void uso(long entrada, long saida, long cache) {
            uso = uso.mais(new UsoTokens(entrada, saida, cache));
        }

        @Override
        public boolean cancelado() {
            // Não é writer.fechado(): queda de rede não para o turno (a resposta é gravada e relida).
            return ativo.cancelado.get() || clock.instant().isAfter(ativo.prazo);
        }

        @Override
        public AssistenteLlmPort.ResultadoFerramenta ferramenta(String nome, String inputJson) {
            Ferramenta f = ferramentas.get(nome);
            if (f == null) {
                return AssistenteLlmPort.ResultadoFerramenta.falha("Ferramenta desconhecida: " + nome);
            }
            String etapa = "t" + (++etapas);
            writer.ferramenta(f.rotulo(), true);
            writer.etapa(etapa, f.rotulo(), "inicio");
            boolean falhou = true;
            Timer.Sample duracao = Timer.start(metricas);
            try {
                AssistenteLlmPort.ResultadoFerramenta r = publicar(executar(f, inputJson));
                falhou = r.erro();
                return r;
            } finally {
                duracao.stop(Timer.builder("assistente.ferramenta.duracao")
                        .description("Duração de uma ferramenta do assistente")
                        .tag("nome", f.nome())
                        .register(metricas));
                writer.ferramenta(f.rotulo(), false);
                writer.etapa(etapa, f.rotulo(), falhou ? "falha" : "fim");
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
                blocosPendentes.add(bloco);
            }
            for (AssistenteLinkDTO link : r.links()) {
                if (AssistenteMapper.rotaSegura(link.rota())) {
                    links.add(link);
                    linksPendentes.add(link);
                }
            }
            return r.erro()
                    ? AssistenteLlmPort.ResultadoFerramenta.falha(r.paraModelo())
                    : AssistenteLlmPort.ResultadoFerramenta.ok(r.paraModelo());
        }
    }
}
