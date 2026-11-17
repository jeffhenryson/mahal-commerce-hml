package com.cernecommerce.infra.handler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cernecommerce.core.ports.out.notification.DevAlertPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fim normal de um stream SSE não é erro 500: prazo vencido ({@link AsyncRequestTimeoutException})
 * e cliente que desconectou ({@link AsyncRequestNotUsableException}) caíam no handler genérico, que
 * gravava ERROR, alertava os devs e tentava devolver JSON numa resposta {@code text/event-stream} —
 * o que gerava um segundo erro, {@link HttpMediaTypeNotAcceptableException}, e um 503.
 */
class GlobalExceptionHandlerSseTest {

    private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private DevAlertPort devAlerts;
    private MockMvc mockMvc;

    @RestController
    static class StreamQueFalha {

        @GetMapping(value = "/teste/stream-prazo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter prazo() {
            throw new AsyncRequestTimeoutException();
        }

        @GetMapping(value = "/teste/stream-cliente-saiu", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter clienteSaiu() throws java.io.IOException {
            throw new AsyncRequestNotUsableException("ServletOutputStream failed to flush: Broken pipe");
        }

        @GetMapping(value = "/teste/stream-defeito", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        SseEmitter defeito() {
            throw new IllegalStateException("boom");
        }
    }

    @BeforeEach
    void setUp() {
        devAlerts = mock(DevAlertPort.class);
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(handler, "devAlerts", devAlerts);
        mockMvc = MockMvcBuilders.standaloneSetup(new StreamQueFalha())
                .setControllerAdvice(handler)
                .build();
        logs.start();
        handlerLogger.addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        handlerLogger.detachAppender(logs);
        logs.stop();
    }

    @Test
    void prazoDoStream_naoGravaErro_naoAlerta_eNaoEscreveCorpo() throws Exception {
        MvcResult result = mockMvc.perform(get("/teste/stream-prazo").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andReturn();

        assertTratadaSemRuido(result, AsyncRequestTimeoutException.class);
    }

    @Test
    void clienteQueDesconectou_naoGravaErro_naoAlerta_eNaoEscreveCorpo() throws Exception {
        MvcResult result = mockMvc.perform(get("/teste/stream-cliente-saiu").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andReturn();

        assertTratadaSemRuido(result, AsyncRequestNotUsableException.class);
    }

    /** Controle: defeito de verdade no mesmo tipo de rota continua indo para o handler genérico. */
    @Test
    void outraExcecaoNoStream_continuaGravandoErro() throws Exception {
        // O handler genérico tenta devolver ApiError em JSON numa rota text/event-stream e não
        // consegue; sem resolver de reserva no MockMvc standalone, a exceção original escapa do
        // perform(). O que interessa aqui é só que ele foi chamado e gravou o ERROR.
        try {
            mockMvc.perform(get("/teste/stream-defeito").accept(MediaType.TEXT_EVENT_STREAM));
        } catch (Exception esperadaNoStandalone) {
            assertThat(esperadaNoStandalone).hasRootCauseInstanceOf(IllegalStateException.class);
        }

        assertThat(logs.list).extracting(ILoggingEvent::getLevel).contains(Level.ERROR);
    }

    private void assertTratadaSemRuido(MvcResult result, Class<? extends Exception> esperada) {
        // A exceção que chegou ao fim é a original: nenhuma HttpMediaTypeNotAcceptableException por cima.
        assertThat(result.getResolvedException()).isInstanceOf(esperada)
                .isNotInstanceOf(HttpMediaTypeNotAcceptableException.class);
        assertThat(logs.list).extracting(ILoggingEvent::getLevel).doesNotContain(Level.ERROR, Level.WARN);
        verifyNoInteractions(devAlerts);
    }
}
