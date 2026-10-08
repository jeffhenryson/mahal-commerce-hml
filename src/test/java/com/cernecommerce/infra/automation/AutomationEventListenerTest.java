package com.cernecommerce.infra.automation;

import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.event.AuditEvent.EventType;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AutomationEventListenerTest {

    private static final Instant AT = Instant.parse("2026-10-07T15:00:00Z");

    private static AuditEvent event(EventType type, Map<String, Object> details) {
        return new AuditEvent(type, "admin", AT, details);
    }

    private static AutomationOccurrence occurrence(EventType type, Map<String, Object> details) {
        return AutomationEventListener.toOccurrence(event(type, details)).orElseThrow();
    }

    @Test
    void entradaDeEstagio_porCliente_estagioEDia() {
        AutomationOccurrence o = occurrence(EventType.CUSTOMER_STAGE_CHANGED,
                Map.of("customerId", "10", "estagio", "INATIVO"));

        assertThat(o.gatilho()).isEqualTo(CampaignTrigger.ENTRADA_ESTAGIO);
        assertThat(o.estagio()).isEqualTo(CustomerStage.INATIVO);
        assertThat(o.customerId()).isEqualTo(10L);
        assertThat(o.key()).isEqualTo("STAGE:INATIVO:2026-10-07");
    }

    @Test
    void clienteCriado_aceitaIdComoStringOuNumero() {
        assertThat(occurrence(EventType.CUSTOMER_CREATED, Map.of("customerId", "7")).customerId()).isEqualTo(7L);
        AutomationOccurrence loja = occurrence(EventType.CUSTOMER_MARKETPLACE_REGISTERED, Map.of("customerId", 8L));
        assertThat(loja.evento()).isEqualTo(AutomationEvent.CLIENTE_CRIADO);
        assertThat(loja.key()).isEqualTo("CLIENTE:8");
    }

    @Test
    void pedidoConcluido_soNosEstadosFinais() {
        assertThat(occurrence(EventType.ORDER_STATUS_CHANGED, Map.of("orderId", 5L, "to", "ENTREGUE")).evento())
                .isEqualTo(AutomationEvent.PEDIDO_CONCLUIDO);
        assertThat(occurrence(EventType.ORDER_STATUS_CHANGED, Map.of("orderId", 5L, "to", "CONCLUIDO")).key())
                .isEqualTo("PEDIDO:5");
        assertThat(AutomationEventListener.toOccurrence(event(EventType.ORDER_STATUS_CHANGED,
                Map.of("orderId", 5L, "to", "SEPARADO")))).isEmpty();
    }

    @Test
    void mapeiaOsDemaisEventos() {
        assertThat(occurrence(EventType.ORDER_CREATED, Map.of("orderId", 1L)).evento()).isEqualTo(AutomationEvent.PEDIDO_CRIADO);
        assertThat(occurrence(EventType.ORDER_REFUNDED, Map.of("orderId", 1L)).evento()).isEqualTo(AutomationEvent.PEDIDO_CANCELADO);
        assertThat(occurrence(EventType.PAYMENT_APPROVED, Map.of("orderId", 1L)).key()).isEqualTo("PAGTO:1:OK");
        assertThat(occurrence(EventType.PAYMENT_DECLINED, Map.of("orderId", 1L, "transactionNsu", "t9")).key())
                .isEqualTo("PAGTO:1:NOK:t9");
        AutomationOccurrence venda = occurrence(EventType.PDV_SALE_COMPLETED, Map.of("orderId", 2L, "customerId", 10L));
        assertThat(venda.evento()).isEqualTo(AutomationEvent.VENDA_PDV_CONCLUIDA);
        assertThat(venda.customerId()).isEqualTo(10L);
        AutomationOccurrence caixa = occurrence(EventType.CASH_SESSION_CLOSED,
                Map.of("sessionId", 3L, "countedAmount", 100, "notes", "não vai no contexto"));
        assertThat(caixa.evento()).isEqualTo(AutomationEvent.CAIXA_FECHADO);
        assertThat(caixa.contexto()).containsKeys("sessionId", "countedAmount").doesNotContainKey("notes");
        assertThat(occurrence(EventType.STOCK_BELOW_REORDER_POINT, Map.of("sku", "ESS-01")).key())
                .isEqualTo("ESTOQUE:ESS-01:-:2026-10-07");
    }

    @Test
    void comanda_fechadaEFinalizada_compartilhamAChavePorPedido() {
        AutomationOccurrence closed = occurrence(EventType.COMANDA_CLOSED,
                Map.of("comandaId", 4L, "orderId", 9L, "customerId", 10L));
        AutomationOccurrence finished = occurrence(EventType.COMANDA_FINISHED, Map.of("comandaId", 4L, "orderId", 9L));
        assertThat(closed.evento()).isEqualTo(AutomationEvent.COMANDA_FECHADA);
        assertThat(closed.key()).isEqualTo(finished.key()).isEqualTo("COMANDA:4:9");
    }

    @Test
    void eventosSemRelacao_ouSemId_naoGeramOcorrencia() {
        assertThat(AutomationEventListener.toOccurrence(event(EventType.USER_LOGGED_IN, Map.of()))).isEmpty();
        assertThat(AutomationEventListener.toOccurrence(event(EventType.ORDER_CREATED, Map.of()))).isEmpty();
        assertThat(AutomationEventListener.toOccurrence(event(EventType.CUSTOMER_STAGE_CHANGED,
                Map.of("customerId", "10", "estagio", "INEXISTENTE")))).isEmpty();
    }

    @Test
    void falhaNoDispatcher_naoPropaga() {
        AutomationDispatchUseCase dispatcher = mock(AutomationDispatchUseCase.class);
        doThrow(new IllegalStateException("boom")).when(dispatcher).dispatch(any());
        AutomationEventListener listener = new AutomationEventListener(dispatcher);

        listener.onAuditEvent(event(EventType.CUSTOMER_CREATED, Map.of("customerId", 1L)));

        verify(dispatcher).dispatch(any());
    }

    @Test
    void asLong_toleraFormatos() {
        assertThat(AutomationEventListener.asLong("12")).isEqualTo(12L);
        assertThat(AutomationEventListener.asLong(12)).isEqualTo(12L);
        assertThat(AutomationEventListener.asLong("abc")).isNull();
        assertThat(AutomationEventListener.asLong(null)).isNull();
    }
}
