package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.model.cashback.CashbackBalance;
import com.cernecommerce.core.domain.model.config.AutomationPlatformTarget;
import com.cernecommerce.core.domain.model.config.WhatsappCredentials;
import com.cernecommerce.core.domain.model.config.WhatsappSendResult;
import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDelivery;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.domain.model.pedido.OrderStatus;
import com.cernecommerce.core.domain.model.pedido.SalesChannel;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutomationDispatchServiceTest {

    @Mock CampaignAutomationRepository automationRepository;
    @Mock CampaignLogRepository logRepository;
    @Mock CustomerRepository customerRepository;
    @Mock CustomerTagRepository customerTagRepository;
    @Mock CashbackUseCase cashbackUseCase;
    @Mock OrderRepository orderRepository;
    @Mock CampaignWebhookPort webhookPort;
    @Mock WhatsappPort whatsappPort;
    @Mock WhatsappIntegrationUseCase whatsappIntegration;
    @Mock AutomationPlatformIntegrationUseCase platformIntegration;

    AutomationDispatchService service;

    private static final Customer MARIA = Customer.of(10L, "Maria Silva", "(85) 99999-1234", "maria@x.com", null,
            null, Instant.now(), CustomerStage.CLIENTE_ATIVO);

    @BeforeEach
    void setUp() {
        service = new AutomationDispatchService(automationRepository, logRepository, customerRepository,
                customerTagRepository, cashbackUseCase, orderRepository, webhookPort, whatsappPort,
                whatsappIntegration, platformIntegration, new CampaignTemplateRenderer());
        when(customerRepository.findById(10L)).thenReturn(Optional.of(MARIA));
        when(cashbackUseCase.getCustomerBalance(any())).thenReturn(new CashbackBalance(new BigDecimal("48.50"),
                BigDecimal.ZERO, BigDecimal.ZERO));
        when(customerTagRepository.findTagsByCustomerId(any())).thenReturn(List.of());
        when(logRepository.claim(any())).thenAnswer(inv -> Optional.of(inv.getArgument(0)));
        when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static CampaignAutomation evento(Long id, AutomationEvent evento, CustomerStage segmento,
            AutomationDelivery entrega, List<AutomationMetadata> metadados) {
        return new CampaignAutomation(id, "Automação " + id, CampaignTrigger.EVENTO, evento, segmento,
                CampaignChannel.WHATSAPP, "Oi {{cliente.primeiroNome}}, pedido {{pedido.numero}}", true,
                Instant.now(), entrega, metadados);
    }

    private static AutomationDelivery webhook(String url) {
        return new AutomationDelivery(AutomationDestination.WEBHOOK, url, null, null, null, AutomationAuthType.BEARER,
                null, "abcd", Map.of("Authorization", "Bearer segredo-abcd"));
    }

    private static Order order(Long id, Long customerId) {
        Order order = mock(Order.class);
        when(order.id()).thenReturn(id);
        when(order.customerId()).thenReturn(customerId);
        when(order.orderNumber()).thenReturn("000123");
        when(order.netAmount()).thenReturn(new BigDecimal("189.90"));
        when(order.status()).thenReturn(OrderStatus.ENTREGUE);
        when(order.channel()).thenReturn(SalesChannel.MARKETPLACE);
        when(order.items()).thenReturn(List.of());
        return order;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sentPayload() {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(webhookPort).send(anyString(), any(), payload.capture());
        return (Map<String, Object>) payload.getValue();
    }

    // ── gatilho por evento ────────────────────────────────────────────────────

    @Test
    void evento_dePedido_resolveClientePeloPedido_eEnviaSoOsBlocosEscolhidos() {
        CampaignAutomation automation = evento(1L, AutomationEvent.PEDIDO_CONCLUIDO, null,
                webhook("https://n8n.x.com/hook"), List.of(AutomationMetadata.CLIENTE, AutomationMetadata.PEDIDO));
        when(automationRepository.findActiveByEvent(AutomationEvent.PEDIDO_CONCLUIDO)).thenReturn(List.of(automation));
        Order order = order(55L, 10L);
        when(orderRepository.findById(55L)).thenReturn(Optional.of(order));
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.ok(200));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.PEDIDO_CONCLUIDO, null, 55L, "PEDIDO:55", null));

        verify(webhookPort).send(eq("https://n8n.x.com/hook"), eq(Map.of("Authorization", "Bearer segredo-abcd")), any());
        Map<String, Object> payload = sentPayload();
        assertThat(payload).containsEntry("evento", "PEDIDO_CONCLUIDO")
                .containsEntry("mensagem", "Oi Maria, pedido 000123")
                .containsKeys("cliente", "pedido")
                .doesNotContainKeys("loja", "data", "automacao");
        assertThat((Map<String, Object>) payload.get("cliente")).containsEntry("whatsapp", "5585999991234");
        assertThat((Map<String, Object>) payload.get("pedido")).containsEntry("numero", "000123");

        ArgumentCaptor<CampaignLogEntry> claim = ArgumentCaptor.forClass(CampaignLogEntry.class);
        verify(logRepository).claim(claim.capture());
        assertThat(claim.getValue().eventKey()).isEqualTo("PEDIDO:55|c10");
        assertThat(claim.getValue().customerId()).isEqualTo(10L);
        verify(logRepository).save(argThat(e -> e.status() == CampaignDispatchStatus.ENVIADO));
    }

    @Test
    void ocorrenciaJaReservada_naoDisparaDeNovo() {
        CampaignAutomation automation = evento(1L, AutomationEvent.CLIENTE_CRIADO, null, webhook("https://x.com/h"), null);
        when(automationRepository.findActiveByEvent(AutomationEvent.CLIENTE_CRIADO)).thenReturn(List.of(automation));
        // doReturn, e não when(...): when(claim(any())) chamaria claim(null) e rodaria a resposta do
        // setUp, que faz Optional.of(null) e lança NPE antes de o teste começar.
        doReturn(Optional.empty()).when(logRepository).claim(any());

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, 10L, null, "CLIENTE:10", null));

        verifyNoInteractions(webhookPort);
        verify(logRepository, never()).save(any());
    }

    @Test
    void segmentoAlvo_filtraNoEvento() {
        CampaignAutomation soInativos = evento(1L, AutomationEvent.CLIENTE_CRIADO, CustomerStage.INATIVO,
                webhook("https://x.com/a"), null);
        CampaignAutomation qualquer = evento(2L, AutomationEvent.CLIENTE_CRIADO, null, webhook("https://x.com/b"), null);
        when(automationRepository.findActiveByEvent(AutomationEvent.CLIENTE_CRIADO))
                .thenReturn(List.of(soInativos, qualquer));
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.ok(200));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, 10L, null, "CLIENTE:10", null));

        verify(webhookPort, never()).send(eq("https://x.com/a"), any(), any());
        verify(webhookPort).send(eq("https://x.com/b"), any(), any());
    }

    @Test
    void eventoComCliente_semCliente_naoDispara() {
        when(automationRepository.findActiveByEvent(AutomationEvent.COMANDA_FECHADA)).thenReturn(List.of(
                evento(1L, AutomationEvent.COMANDA_FECHADA, null, webhook("https://x.com/h"), null)));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.COMANDA_FECHADA, null, null, "COMANDA:1:-", null));

        verifyNoInteractions(webhookPort);
        verify(logRepository, never()).claim(any());
    }

    @Test
    void eventoDaLoja_vaiSemCliente_eComContexto() {
        CampaignAutomation automation = evento(1L, AutomationEvent.CAIXA_FECHADO, CustomerStage.INATIVO,
                webhook("https://x.com/h"), null);
        when(automationRepository.findActiveByEvent(AutomationEvent.CAIXA_FECHADO)).thenReturn(List.of(automation));
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.ok(200));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CAIXA_FECHADO, null, null, "CAIXA:7",
                Map.of("sessionId", 7L, "differenceAmount", BigDecimal.TEN)));

        Map<String, Object> payload = sentPayload();
        assertThat(payload).doesNotContainKey("cliente").containsKey("contexto");
        verify(logRepository).claim(argThat(e -> e.customerId() == null && "CAIXA:7".equals(e.eventKey())));
    }

    @Test
    void entradaDeEstagio_disparaSoAsDoEstagio() {
        CampaignAutomation ativo = new CampaignAutomation(1L, "Ativo", CampaignTrigger.ENTRADA_ESTAGIO, null,
                CustomerStage.CLIENTE_ATIVO, CampaignChannel.EMAIL, "Oi", true, Instant.now(),
                webhook("https://x.com/ativo"), null);
        CampaignAutomation inativo = new CampaignAutomation(2L, "Inativo", CampaignTrigger.ENTRADA_ESTAGIO, null,
                CustomerStage.INATIVO, CampaignChannel.EMAIL, "Oi", true, Instant.now(),
                webhook("https://x.com/inativo"), null);
        when(automationRepository.findActiveStageEntry()).thenReturn(List.of(ativo, inativo));
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.ok(200));

        service.dispatch(AutomationOccurrence.stageEntered(10L, CustomerStage.CLIENTE_ATIVO, "STAGE:CLIENTE_ATIVO:2026-10-07"));

        verify(webhookPort).send(eq("https://x.com/ativo"), any(), any());
        verify(webhookPort, never()).send(eq("https://x.com/inativo"), any(), any());
        assertThat(sentPayload()).containsEntry("evento", "ENTRADA_ESTAGIO");
    }

    @Test
    void falhaDeEntrega_ficaNoLogComoFalha() {
        when(automationRepository.findActiveByEvent(AutomationEvent.CLIENTE_CRIADO)).thenReturn(List.of(
                evento(1L, AutomationEvent.CLIENTE_CRIADO, null, webhook("https://x.com/h"), null)));
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.failure(503, "HTTP 503"));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, 10L, null, "CLIENTE:10", null));

        verify(logRepository).save(argThat(e -> e.status() == CampaignDispatchStatus.FALHA
                && "HTTP 503".equals(e.erroDetalhe()) && "CLIENTE:10|c10".equals(e.eventKey())));
    }

    // ── destinos ──────────────────────────────────────────────────────────────

    @Test
    void plataforma_postaEmBaseUrlMaisWorkflow_comBearer() {
        AutomationDelivery plataforma = new AutomationDelivery(AutomationDestination.PLATAFORMA, null, "pos-venda",
                null, null, null, null, null, null);
        when(automationRepository.findActiveByEvent(AutomationEvent.CLIENTE_CRIADO)).thenReturn(List.of(
                evento(1L, AutomationEvent.CLIENTE_CRIADO, null, plataforma, null)));
        when(platformIntegration.activeTarget()).thenReturn(Optional.of(
                new AutomationPlatformTarget("https://n8n.x.com/webhook/", "tk")));
        when(webhookPort.send(any(), any(), any())).thenReturn(WebhookDispatchResult.ok(200));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, 10L, null, "CLIENTE:10", null));

        verify(webhookPort).send(eq("https://n8n.x.com/webhook/pos-venda"), eq(Map.of("Authorization", "Bearer tk")), any());
    }

    @Test
    void plataformaDesativada_falhaSemEnviar() {
        AutomationDelivery plataforma = new AutomationDelivery(AutomationDestination.PLATAFORMA, null, "pos-venda",
                null, null, null, null, null, null);
        when(automationRepository.findActiveByEvent(AutomationEvent.CLIENTE_CRIADO)).thenReturn(List.of(
                evento(1L, AutomationEvent.CLIENTE_CRIADO, null, plataforma, null)));
        when(platformIntegration.activeTarget()).thenReturn(Optional.empty());

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, 10L, null, "CLIENTE:10", null));

        verifyNoInteractions(webhookPort);
        verify(logRepository).save(argThat(e -> e.status() == CampaignDispatchStatus.FALHA));
    }

    @Test
    void whatsappMeta_enviaTemplateComAMensagemComoParametro() {
        AutomationDelivery meta = new AutomationDelivery(AutomationDestination.WHATSAPP_META, null, null,
                "promo_cashback", null, null, null, null, null);
        when(automationRepository.findActiveByEvent(AutomationEvent.CLIENTE_CRIADO)).thenReturn(List.of(
                evento(1L, AutomationEvent.CLIENTE_CRIADO, null, meta, null)));
        when(whatsappIntegration.activeCredentials()).thenReturn(Optional.of(new WhatsappCredentials("123456", "EAAG")));
        when(whatsappPort.sendTemplate(any(), any(), any(), any(), any(), any())).thenReturn(WhatsappSendResult.ok("wamid.1"));

        service.dispatch(AutomationOccurrence.event(AutomationEvent.CLIENTE_CRIADO, 10L, null, "CLIENTE:10", null));

        verify(whatsappPort).sendTemplate("123456", "EAAG", "5585999991234", "promo_cashback", "pt_BR",
                List.of("Oi Maria, pedido {{pedido.numero}}"));
        verify(logRepository).save(argThat(e -> e.status() == CampaignDispatchStatus.ENVIADO));
        verifyNoInteractions(webhookPort);
    }

    // ── manual e teste ────────────────────────────────────────────────────────

    @Test
    void teste_whatsappMeta_naoEnviaParaClienteFicticio() {
        CampaignAutomation automation = evento(1L, AutomationEvent.CLIENTE_CRIADO, null,
                new AutomationDelivery(AutomationDestination.WHATSAPP_META, null, null, "promo", null, null, null,
                        null, null), null);

        WebhookTestResult result = service.test(automation);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("Integrações");
        verifyNoInteractions(whatsappPort, webhookPort);
    }

    @Test
    void manual_semWebhook_soRegistraPendente() {
        CampaignAutomation legado = CampaignAutomation.of(1L, "Legado", CampaignTrigger.MANUAL,
                CustomerStage.CLIENTE_ATIVO, CampaignChannel.EMAIL, "Oi", true, Instant.now(), null, Map.of());
        when(customerRepository.findByEstagio(CustomerStage.CLIENTE_ATIVO)).thenReturn(List.of(MARIA));

        List<CampaignLogEntry> logs = service.dispatchManual(legado);

        assertThat(logs).singleElement().satisfies(e ->
                assertThat(e.status()).isEqualTo(CampaignDispatchStatus.PENDENTE_INTEGRACAO));
        verifyNoInteractions(webhookPort, cashbackUseCase);
    }

    @Test
    void eventKey_longoETruncado() {
        String key = AutomationDispatchService.eventKey(
                AutomationOccurrence.event(AutomationEvent.ESTOQUE_BAIXO, null, null, "X".repeat(300), null), null);
        assertThat(key).hasSize(160);
    }
}
