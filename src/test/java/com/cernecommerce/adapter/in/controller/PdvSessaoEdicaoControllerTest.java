package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.in.converter.ComandaDTOConverter;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.pdv.SessionNotAwaitingPaymentException;
import com.cernecommerce.core.domain.exception.pdv.SessionNotEditableException;
import com.cernecommerce.core.domain.exception.pdv.SessionPaidPriceChangeException;
import com.cernecommerce.core.domain.model.pdv.Charcoal;
import com.cernecommerce.core.domain.model.pdv.Comanda;
import com.cernecommerce.core.domain.model.pdv.ComandaItem;
import com.cernecommerce.core.domain.model.pdv.ComandaStatus;
import com.cernecommerce.core.domain.model.pdv.SessionProgress;
import com.cernecommerce.core.domain.model.pdv.SessionSetup;
import com.cernecommerce.core.domain.model.pdv.SessionStatus;
import com.cernecommerce.core.domain.model.pedido.ConsumptionMode;
import com.cernecommerce.core.ports.in.ComandaUseCase;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.SessionMenuUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PDV-F057 a PDV-F059 — os endpoints da sessão: resposta, auditoria e os códigos de erro novos. */
class PdvSessaoEdicaoControllerTest {

    private MockMvc mockMvc;
    private ComandaUseCase comandaUseCase;
    private ApplicationEventPublisher publisher;

    private static final UsernamePasswordAuthenticationToken AUTH_COURTESY =
            new UsernamePasswordAuthenticationToken("gerente", null,
                    List.of(new SimpleGrantedAuthority("PDV_COMANDA_COURTESY")));

    private static final UsernamePasswordAuthenticationToken AUTH_PAY_LATER =
            new UsernamePasswordAuthenticationToken("gerente", null,
                    List.of(new SimpleGrantedAuthority("PDV_SESSION_PAY_LATER")));

    private static final String EDICAO = "{\"tierId\":1,\"essencia\":\"Zomo Blueberry\",\"essenciaSku\":\"ZOMO-BLUE\","
            + "\"proporcao\":\"MEIO_A_MEIO\",\"vasoGrande\":false,\"carvao\":\"JUMBO\",\"carvaoCortesia\":true,"
            + "\"adicionalIds\":[7]}";

    @BeforeEach
    void setup() {
        comandaUseCase = mock(ComandaUseCase.class);
        CrmUseCase crmUseCase = mock(CrmUseCase.class);
        when(crmUseCase.findCustomerNames(anyCollection())).thenReturn(Map.of());
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PdvSessaoController(mock(SessionMenuUseCase.class), comandaUseCase, crmUseCase,
                        new ComandaDTOConverter(), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static ComandaItem sessao(SessionProgress progress, SessionSetup setup, BigDecimal price) {
        return ComandaItem.of(1L, "SESS-1", BigDecimal.ONE, price, null, "Sessão Tradicional", Instant.now(),
                ConsumptionMode.SESSAO, false, null, "Zomo Blueberry", null, null, null, null, null, null, null,
                progress, setup);
    }

    private static Comanda comanda(ComandaItem item) {
        return Comanda.of(10L, 1L, "LOJA-01", "Mesa 4", ComandaStatus.ABERTA, List.of(item), null, "caixa1",
                Instant.now(), null);
    }

    @Test
    void editSession_returns_200_withCarvaoCortesia_andAuditsBeforeAndAfter() throws Exception {
        ComandaItem antes = sessao(SessionProgress.awaitingPayment(), new SessionSetup(Charcoal.JUMBO, List.of()),
                new BigDecimal("33.00"));
        ComandaItem depois = sessao(SessionProgress.awaitingPayment(),
                new SessionSetup(Charcoal.JUMBO, List.of(), false, true), new BigDecimal("25.00"));
        when(comandaUseCase.editSession(eq(10L), eq(1L), any(), anyString()))
                .thenReturn(new ComandaUseCase.SessionEdited(comanda(depois), antes, depois));

        mockMvc.perform(put("/pdv/comandas/10/sessoes/1").principal(AUTH_COURTESY)
                        .contentType(MediaType.APPLICATION_JSON).content(EDICAO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].carvaoCortesia").value(true))
                .andExpect(jsonPath("$.items[0].compraNaLoja").value(false));

        ArgumentCaptor<ComandaUseCase.EditSessionCommand> command =
                ArgumentCaptor.forClass(ComandaUseCase.EditSessionCommand.class);
        verify(comandaUseCase).editSession(eq(10L), eq(1L), command.capture(), eq("gerente"));
        assertThat(command.getValue().carvaoCortesia()).isTrue();
        assertThat(command.getValue().adicionalIds()).containsExactly(7L);

        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(AuditEvent.EventType.COMANDA_SESSION_EDITED);
        assertThat(event.getValue().details()).containsKeys("antes", "depois");
    }

    @Test
    void editSession_delivered_returns_409_SESSION_NOT_EDITABLE() throws Exception {
        when(comandaUseCase.editSession(eq(10L), eq(1L), any(), anyString()))
                .thenThrow(new SessionNotEditableException(1L, SessionStatus.ENTREGUE));

        mockMvc.perform(put("/pdv/comandas/10/sessoes/1").principal(AUTH_COURTESY)
                        .contentType(MediaType.APPLICATION_JSON).content(EDICAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_EDITABLE"));
    }

    @Test
    void editSession_paidWithANewPrice_returns_409_SESSION_PAID_PRICE_CHANGE() throws Exception {
        when(comandaUseCase.editSession(eq(10L), eq(1L), any(), anyString()))
                .thenThrow(new SessionPaidPriceChangeException(1L, new BigDecimal("25.00"), new BigDecimal("35.00")));

        mockMvc.perform(put("/pdv/comandas/10/sessoes/1").principal(AUTH_COURTESY)
                        .contentType(MediaType.APPLICATION_JSON).content(EDICAO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_PAID_PRICE_CHANGE"));
    }

    @Test
    void convertToPayLater_returns_200() throws Exception {
        when(comandaUseCase.convertSessionToPayLater(10L, 1L, "gerente")).thenReturn(comanda(
                sessao(SessionProgress.preparingPayLater(Instant.now()), null, new BigDecimal("25.00"))));

        mockMvc.perform(post("/pdv/comandas/10/sessoes/1/pagar-no-final").principal(AUTH_PAY_LATER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].pagarNoFinal").value(true))
                .andExpect(jsonPath("$.items[0].sessionStatus").value("PREPARANDO"));
    }

    @Test
    void convertToPayLater_notAwaitingPayment_returns_409() throws Exception {
        when(comandaUseCase.convertSessionToPayLater(10L, 1L, "gerente"))
                .thenThrow(new SessionNotAwaitingPaymentException(1L));

        mockMvc.perform(post("/pdv/comandas/10/sessoes/1/pagar-no-final").principal(AUTH_PAY_LATER))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SESSION_NOT_AWAITING_PAYMENT"));
    }
}
