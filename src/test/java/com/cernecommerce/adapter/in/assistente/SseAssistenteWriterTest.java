package com.cernecommerce.adapter.in.assistente;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SseAssistenteWriterTest {

    private final ScheduledExecutorService agendador = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void fechar() {
        agendador.shutdownNow();
    }

    @Test
    void heartbeat_escreve_enquanto_aberto_e_para_no_concluir() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        SseAssistenteWriter writer = new SseAssistenteWriter(emitter, "t", agendador, Duration.ofMillis(20), () -> { });

        Thread.sleep(150);
        verify(emitter, atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));

        writer.concluir();
        verify(emitter).complete();
        clearInvocations(emitter);
        Thread.sleep(100);
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void falha_de_envio_fecha_o_writer_conta_uma_queda_e_vira_no_op() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("Broken pipe")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        AtomicInteger quedas = new AtomicInteger();
        SseAssistenteWriter writer = new SseAssistenteWriter(emitter, "t", null, null, quedas::incrementAndGet);

        writer.texto("a");
        writer.texto("b");
        writer.concluir();

        assertThat(writer.fechado()).isTrue();
        assertThat(quedas).hasValue(1);
        verify(emitter, never()).complete();
    }

    @Test
    void sem_queda_quando_o_servidor_conclui() {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicInteger quedas = new AtomicInteger();
        SseAssistenteWriter writer = new SseAssistenteWriter(emitter, "t", null, null, quedas::incrementAndGet);
        writer.texto("ok");
        writer.concluir();
        assertThat(quedas).hasValue(0);
    }
}
