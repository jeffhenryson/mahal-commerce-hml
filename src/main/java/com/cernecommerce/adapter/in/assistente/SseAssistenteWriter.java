package com.cernecommerce.adapter.in.assistente;

import com.cernecommerce.adapter.in.dtos.response.AssistenteAcaoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteBlocoDTO;
import com.cernecommerce.adapter.in.dtos.response.AssistenteLinkDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escreve os eventos do contrato ({@code event: <nome>} + {@code data: <json>}) num
 * {@link SseEmitter}. O emitter não é thread-safe: tudo passa por aqui, sincronizado. Quando o
 * cliente desconecta, as escritas viram no-op e {@link #fechado()} avisa o turno para parar.
 */
final class SseAssistenteWriter {

    private static final Logger log = LoggerFactory.getLogger(SseAssistenteWriter.class);

    private final SseEmitter emitter;
    private volatile boolean fechado;

    SseAssistenteWriter(SseEmitter emitter) {
        this.emitter = emitter;
        emitter.onCompletion(() -> fechado = true);
        emitter.onTimeout(() -> fechado = true);
        emitter.onError(e -> fechado = true);
    }

    boolean fechado() {
        return fechado;
    }

    void texto(String delta) {
        enviar("texto", Map.of("delta", delta));
    }

    void ferramenta(String rotulo, boolean inicio) {
        enviar("ferramenta", Map.of("rotulo", rotulo, "estado", inicio ? "inicio" : "fim"));
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
        if (fechado) {
            return;
        }
        fechado = true;
        try {
            emitter.complete();
        } catch (RuntimeException ex) {
            log.debug("assistente.sse.complete.falhou erro={}", ex.getMessage());
        }
    }

    private synchronized void enviar(String nome, Object dados) {
        if (fechado) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(nome).data(FerramentaSupport.MAPPER.writeValueAsString(dados)));
        } catch (Exception ex) {
            // Cliente fechou a aba ou abortou o fetch.
            fechado = true;
            log.debug("assistente.sse.envio.falhou evento={} erro={}", nome, ex.getMessage());
        }
    }
}
