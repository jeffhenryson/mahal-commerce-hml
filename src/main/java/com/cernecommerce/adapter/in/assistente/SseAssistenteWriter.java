package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Escreve os eventos do contrato ({@code event: <nome>} + {@code data: <json>}) num
 * {@link SseEmitter}. O emitter não é thread-safe: tudo passa por aqui, sincronizado — inclusive o
 * heartbeat.
 *
 * <p>Quando o cliente desconecta, as escritas viram no-op e {@link #fechado()} passa a responder
 * {@code true}. Isso <b>não</b> cancela o turno: o orquestrador continua, termina e grava a resposta,
 * que o front relê depois. Só o "Parar" explícito (ou o prazo do turno) cancela.</p>
 *
 * <p>Heartbeat: um comentário SSE {@code : ping} a cada {@code heartbeat}, para o proxy (Traefik,
 * Cloudflare) não derrubar a conexão ociosa enquanto o modelo pensa ou a ferramenta roda. O front
 * ignora linhas que começam com {@code :}.</p>
 */
final class SseAssistenteWriter {

    private static final Logger log = LoggerFactory.getLogger(SseAssistenteWriter.class);

    private final SseEmitter emitter;
    private final String turnoId;
    private final long inicio = System.nanoTime();
    private final Runnable aoCair;
    private volatile boolean fechado;
    /** O servidor chamou {@link #concluir()}: o onCompletion que vier depois não é "cliente saiu". */
    private volatile boolean concluidoPeloServidor;
    private volatile boolean quedaRegistrada;
    private volatile ScheduledFuture<?> heartbeat;

    /**
     * @param aoCair chamado uma vez quando a conexão cai antes do fim (métrica); pode ser no-op
     */
    SseAssistenteWriter(SseEmitter emitter, String turnoId, ScheduledExecutorService agendador, Duration intervalo,
            Runnable aoCair) {
        this.emitter = emitter;
        this.turnoId = turnoId;
        this.aoCair = aoCair;
        emitter.onTimeout(() -> {
            log.warn("assistente.sse.timeout turnoId={} ms={}", turnoId, ms());
            fechar(true);
        });
        emitter.onError(e -> {
            log.info("assistente.sse.erro turnoId={} classe={} erro={} ms={}", turnoId,
                    e == null ? null : e.getClass().getSimpleName(), e == null ? null : e.getMessage(), ms());
            fechar(true);
        });
        emitter.onCompletion(() -> {
            if (!concluidoPeloServidor) {
                log.info("assistente.sse.cliente.saiu turnoId={} ms={}", turnoId, ms());
                fechar(true);
            }
        });
        if (agendador != null && intervalo != null && !intervalo.isZero() && !intervalo.isNegative()) {
            long passo = intervalo.toMillis();
            this.heartbeat = agendador.scheduleAtFixedRate(this::ping, passo, passo, TimeUnit.MILLISECONDS);
        }
    }

    /** O cliente não recebe mais nada (desconectou, timeout ou o turno já concluiu). */
    boolean fechado() {
        return fechado;
    }

    void texto(String delta) {
        enviar("texto", Map.of("delta", delta));
    }

    void ferramenta(String rotulo, boolean inicio) {
        enviar("ferramenta", Map.of("rotulo", rotulo, "estado", inicio ? "inicio" : "fim"));
    }

    /** Passo do turno, para a lista de progresso do front: {@code estado} = inicio, fim ou falha. */
    void etapa(String id, String rotulo, String estado) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("id", id);
        dados.put("rotulo", rotulo);
        dados.put("estado", estado);
        enviar("etapa", dados);
    }

    void bloco(AssistenteBlocoDTO bloco) {
        enviar("bloco", bloco);
    }

    void link(AssistenteLinkDTO link) {
        enviar("link", link);
    }

    void acao(AssistenteAcaoDTO acao) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("acaoId", acao.acaoId());
        dados.put("titulo", acao.titulo());
        dados.put("campos", acao.campos());
        dados.put("estado", acao.estado());
        dados.put("expiraEm", acao.expiraEm());
        enviar("acao", dados);
    }

    /** Título que a conversa acabou de ganhar (só no 1º turno), antes de qualquer {@code texto}. */
    void titulo(String titulo) {
        enviar("titulo", Map.of("titulo", titulo));
    }

    /** {@code perguntaId}: a pergunta deste turno, para "gerar de novo" / "editar e reenviar". */
    void fim(String mensagemId, String perguntaId, String provedor, String modelo) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("mensagemId", mensagemId);
        dados.put("perguntaId", perguntaId);
        dados.put("provedor", provedor);
        dados.put("modelo", modelo);
        enviar("fim", dados);
    }

    /** {@code perguntaId} só vem quando a pergunta chegou a ser gravada ("tentar de novo" a substitui). */
    void erro(String codigo, String mensagem, String perguntaId) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("codigo", codigo);
        dados.put("mensagem", mensagem == null ? "Erro inesperado." : mensagem);
        if (perguntaId != null) {
            dados.put("perguntaId", perguntaId);
        }
        enviar("erro", dados);
    }

    synchronized void concluir() {
        pararHeartbeat();
        concluidoPeloServidor = true;
        if (fechado) {
            return;
        }
        fechado = true;
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("assistente.sse.complete.falhou turnoId={} erro={}", turnoId, ex.getMessage());
        }
    }

    long ms() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
    }

    synchronized void ping() {
        if (fechado) {
            pararHeartbeat();
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (Exception ex) {
            log.info("assistente.sse.cliente.saiu turnoId={} em=ping ms={} erro={}", turnoId, ms(), ex.getMessage());
            fechar(true);
        }
    }

    private void fechar(boolean queda) {
        fechado = true;
        pararHeartbeat();
        if (queda && !concluidoPeloServidor && !quedaRegistrada) {
            quedaRegistrada = true;
            try {
                aoCair.run();
            } catch (RuntimeException ignored) {
                // métrica não derruba o turno
            }
        }
    }

    private void pararHeartbeat() {
        ScheduledFuture<?> h = heartbeat;
        if (h != null) {
            h.cancel(false);
        }
    }

    private synchronized void enviar(String nome, Object dados) {
        if (fechado) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(nome).data(FerramentaSupport.MAPPER.writeValueAsString(dados)));
        } catch (Exception ex) {
            // Cliente fechou a aba, abortou o fetch ou a rede caiu. O turno segue e grava.
            log.info("assistente.sse.cliente.saiu turnoId={} evento={} ms={} erro={}", turnoId, nome, ms(),
                    ex.getMessage());
            fechar(true);
        }
    }
}
