package com.cernecommerce.core.service;

import com.cernecommerce.core.domain.exception.crm.AutomationWebhookNotConfiguredException;
import com.cernecommerce.core.domain.model.config.AutomationPlatformTarget;
import com.cernecommerce.core.domain.model.config.WhatsappCredentials;
import com.cernecommerce.core.domain.model.config.WhatsappSendResult;
import com.cernecommerce.core.domain.model.crm.AutomationDelivery;
import com.cernecommerce.core.domain.model.crm.AutomationDispatchContext;
import com.cernecommerce.core.domain.model.crm.AutomationDispatchOutcome;
import com.cernecommerce.core.domain.model.crm.AutomationRecipient;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.AutomationOccurrence;
import com.cernecommerce.core.domain.model.crm.CampaignAutomation;
import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import com.cernecommerce.core.domain.model.crm.CampaignLogEntry;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.Customer;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import com.cernecommerce.core.domain.model.crm.Tag;
import com.cernecommerce.core.domain.model.crm.WebhookDispatchResult;
import com.cernecommerce.core.domain.model.crm.WebhookTestResult;
import com.cernecommerce.core.domain.model.pedido.Order;
import com.cernecommerce.core.ports.in.AutomationDispatchUseCase;
import com.cernecommerce.core.ports.in.AutomationPlatformIntegrationUseCase;
import com.cernecommerce.core.ports.in.CampaignTemplateRendererUseCase;
import com.cernecommerce.core.ports.in.CashbackUseCase;
import com.cernecommerce.core.ports.in.WhatsappIntegrationUseCase;
import com.cernecommerce.core.ports.out.crm.CampaignAutomationRepository;
import com.cernecommerce.core.ports.out.crm.CampaignLogRepository;
import com.cernecommerce.core.ports.out.crm.CampaignWebhookPort;
import com.cernecommerce.core.ports.out.crm.CustomerRepository;
import com.cernecommerce.core.ports.out.crm.CustomerTagRepository;
import com.cernecommerce.core.ports.out.notification.WhatsappPort;
import com.cernecommerce.core.ports.out.pedido.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Entrega das automações do CRM — monta a mensagem (template renderizado) e o payload (só os
 * blocos de {@code metadados}) e entrega pelo destino:
 * <ul>
 *   <li>{@code WEBHOOK}: POST no {@code webhookUrl} com os headers de autenticação da automação;</li>
 *   <li>{@code PLATAFORMA}: POST em {@code {baseUrl}/{workflowPath}} da integração n8n/Make;</li>
 *   <li>{@code WHATSAPP_META}: template aprovado para o WhatsApp do cliente, com a mensagem
 *       renderizada como parâmetro 1 do corpo.</li>
 * </ul>
 *
 * <p>Os gatilhos automáticos ({@link #dispatch}) reservam a ocorrência no log antes de enviar
 * ({@link CampaignLogRepository#claim}): evento repetido não dispara duas vezes. Sem transação
 * aqui de propósito — cada gravação de log é a sua, e a chamada HTTP não segura conexão de banco.</p>
 */
public class AutomationDispatchService implements AutomationDispatchUseCase {

    private static final Logger log = LoggerFactory.getLogger(AutomationDispatchService.class);

    // Payload do webhook de automações — identifica a origem para os workflows externos
    // (n8n/Make) já configurados pelo cliente (ver crm/webhook-disparo-real, F008).
    static final String WEBHOOK_ORIGEM = "mahal-admin";
    static final String LOJA_NOME = "Mahal Tabacaria";
    // Placeholders até existir cálculo de segmento/LTV por cliente (crm/listagem-clientes-rfm).
    private static final String SEGMENTO_PLACEHOLDER = "NOVO";
    private static final BigDecimal LTV_PLACEHOLDER = BigDecimal.ZERO;
    private static final int ERROR_MAX_LENGTH = 500;
    private static final int EVENT_KEY_MAX_LENGTH = 160;

    private static final ZoneId ZONA_BRASIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_PT_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_PT_BR = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<CustomerStage, String> ESTAGIO_LABEL = Map.of(
            CustomerStage.NOVO_LEAD, "Novo Lead",
            CustomerStage.EM_ATENDIMENTO, "Em Atendimento",
            CustomerStage.QUALIFICADO, "Qualificado",
            CustomerStage.CLIENTE_ATIVO, "AutomationRecipient Ativo",
            CustomerStage.INATIVO, "Inativo");

    static final String WHATSAPP_TEST_NOT_SENT = "Destino WhatsApp: o teste não envia mensagem a cliente fictício. "
            + "Teste o envio em Configurações › Integrações › WhatsApp.";

    private final CampaignAutomationRepository automationRepository;
    private final CampaignLogRepository logRepository;
    private final CustomerRepository customerRepository;
    private final CustomerTagRepository customerTagRepository;
    private final CashbackUseCase cashbackUseCase;
    private final OrderRepository orderRepository;
    private final CampaignWebhookPort webhookPort;
    private final WhatsappPort whatsappPort;
    private final WhatsappIntegrationUseCase whatsappIntegration;
    private final AutomationPlatformIntegrationUseCase platformIntegration;
    private final CampaignTemplateRendererUseCase templateRenderer;

    public AutomationDispatchService(CampaignAutomationRepository automationRepository,
            CampaignLogRepository logRepository, CustomerRepository customerRepository,
            CustomerTagRepository customerTagRepository, CashbackUseCase cashbackUseCase,
            OrderRepository orderRepository, CampaignWebhookPort webhookPort, WhatsappPort whatsappPort,
            WhatsappIntegrationUseCase whatsappIntegration, AutomationPlatformIntegrationUseCase platformIntegration,
            CampaignTemplateRendererUseCase templateRenderer) {
        this.automationRepository = automationRepository;
        this.logRepository = logRepository;
        this.customerRepository = customerRepository;
        this.customerTagRepository = customerTagRepository;
        this.cashbackUseCase = cashbackUseCase;
        this.orderRepository = orderRepository;
        this.webhookPort = webhookPort;
        this.whatsappPort = whatsappPort;
        this.whatsappIntegration = whatsappIntegration;
        this.platformIntegration = platformIntegration;
        this.templateRenderer = templateRenderer;
    }

    // ── Manual e teste ────────────────────────────────────────────────────────

    @Override
    public List<CampaignLogEntry> dispatchManual(CampaignAutomation automation) {
        boolean legacyWithoutWebhook = automation.destino() == AutomationDestination.WEBHOOK
                && automation.webhookUrl() == null;
        return customerRepository.findByEstagio(automation.segmentoAlvo()).stream()
                .map(customer -> {
                    if (legacyWithoutWebhook) {
                        // Formato legado: sem webhook não há envio, só o registro.
                        return logRepository.save(CampaignLogEntry.create(automation.id(), customer.id()));
                    }
                    AutomationDispatchOutcome outcome = deliver(automation, new AutomationDispatchContext(automation.gatilho().name(),
                            clienteOf(customer), null, Map.of(), false));
                    return logRepository.save(new CampaignLogEntry(null, automation.id(), customer.id(),
                            outcome.status(), Instant.now(), null, outcome.error(), null));
                })
                .toList();
    }

    @Override
    public WebhookTestResult test(CampaignAutomation automation) {
        AutomationDelivery entrega = automation.entrega();
        if (entrega.missingRequirement() != null) {
            throw new AutomationWebhookNotConfiguredException(automation.id());
        }
        AutomationDispatchContext context = new AutomationDispatchContext(automation.gatilho() == CampaignTrigger.EVENTO
                ? automation.evento().name() : automation.gatilho().name(), testCliente(), null, Map.of(), true);
        AutomationDispatchOutcome outcome = deliver(automation, context);
        return new WebhookTestResult(outcome.status() == CampaignDispatchStatus.ENVIADO, outcome.statusCode(),
                outcome.error(), outcome.payload());
    }

    // ── Gatilhos automáticos ──────────────────────────────────────────────────

    @Override
    public void dispatch(AutomationOccurrence occurrence) {
        List<CampaignAutomation> candidates = occurrence.gatilho() == CampaignTrigger.ENTRADA_ESTAGIO
                ? automationRepository.findActiveStageEntry().stream()
                        .filter(a -> a.segmentoAlvo() == occurrence.estagio()).toList()
                : automationRepository.findActiveByEvent(occurrence.evento());
        if (candidates.isEmpty()) {
            return;
        }

        Order order = occurrence.orderId() == null ? null
                : orderRepository.findById(occurrence.orderId()).orElse(null);
        // Eventos de pedido/venda não carregam o cliente: ele vem do próprio pedido.
        Long customerId = occurrence.customerId() != null ? occurrence.customerId()
                : order == null ? null : order.customerId();
        Customer customer = customerId == null ? null : customerRepository.findById(customerId).orElse(null);
        if (occurrence.requiresCustomer() && customer == null) {
            log.debug("automation.dispatch.skip reason=no-customer event={} key={}", occurrence.eventName(),
                    occurrence.key());
            return;
        }
        AutomationRecipient cliente = customer == null || !occurrence.requiresCustomer() ? null : clienteOf(customer);
        AutomationDispatchContext context = new AutomationDispatchContext(occurrence.eventName(), cliente, order, occurrence.contexto(), false);

        for (CampaignAutomation automation : candidates) {
            if (cliente != null && occurrence.gatilho() == CampaignTrigger.EVENTO
                    && !automation.acceptsStage(customer.estagio())) {
                continue;
            }
            dispatchOne(automation, occurrence, cliente, context);
        }
    }

    private void dispatchOne(CampaignAutomation automation, AutomationOccurrence occurrence, AutomationRecipient cliente,
            AutomationDispatchContext context) {
        Long customerId = cliente == null ? null : cliente.id();
        String eventKey = eventKey(occurrence, customerId);
        Optional<CampaignLogEntry> claimed = logRepository.claim(
                CampaignLogEntry.claim(automation.id(), customerId, eventKey));
        if (claimed.isEmpty()) {
            log.debug("automation.dispatch.duplicate automationId={} key={}", automation.id(), eventKey);
            return;
        }
        AutomationDispatchOutcome outcome;
        try {
            outcome = deliver(automation, context);
        } catch (RuntimeException ex) {
            log.error("automation.dispatch.failed automationId={} key={}", automation.id(), eventKey, ex);
            outcome = AutomationDispatchOutcome.failed(truncate(ex.getMessage()), null, Map.of());
        }
        logRepository.save(claimed.get().withResult(outcome.status(), outcome.error()));
        log.info("automation.dispatch automationId={} event={} destino={} status={}", automation.id(),
                occurrence.eventName(), automation.destino(), outcome.status());
    }

    static String eventKey(AutomationOccurrence occurrence, Long customerId) {
        String key = occurrence.key() + (customerId == null ? "" : "|c" + customerId);
        return key.length() > EVENT_KEY_MAX_LENGTH ? key.substring(0, EVENT_KEY_MAX_LENGTH) : key;
    }

    // ── Entrega ───────────────────────────────────────────────────────────────

    private AutomationDispatchOutcome deliver(CampaignAutomation automation, AutomationDispatchContext context) {
        String mensagem = templateRenderer.render(automation.template(), templateVariables(automation, context));
        Map<String, Object> payload = payload(automation, context, mensagem);
        AutomationDelivery entrega = automation.entrega();
        return switch (entrega.destino()) {
            case WEBHOOK -> {
                if (entrega.webhookUrl() == null) {
                    // Formato legado: sem webhook não há envio, só o registro.
                    yield new AutomationDispatchOutcome(CampaignDispatchStatus.PENDENTE_INTEGRACAO, null, null, payload);
                }
                yield fromWebhook(webhookPort.send(entrega.webhookUrl(), entrega.webhookHeaders(), payload), payload);
            }
            case PLATAFORMA -> {
                Optional<AutomationPlatformTarget> target = platformIntegration.activeTarget();
                if (target.isEmpty()) {
                    yield AutomationDispatchOutcome.failed("Plataforma de automação desativada ou não configurada em Integrações",
                            null, payload);
                }
                yield fromWebhook(webhookPort.send(target.get().urlFor(entrega.workflowPath()),
                        AutomationPlatformIntegrationService.authHeaders(target.get()), payload), payload);
            }
            case WHATSAPP_META -> deliverWhatsapp(entrega, context, mensagem, payload);
        };
    }

    private AutomationDispatchOutcome deliverWhatsapp(AutomationDelivery entrega, AutomationDispatchContext context, String mensagem,
            Map<String, Object> payload) {
        if (context.teste()) {
            return AutomationDispatchOutcome.failed(WHATSAPP_TEST_NOT_SENT, null, payload);
        }
        Optional<WhatsappCredentials> credentials = whatsappIntegration.activeCredentials();
        if (credentials.isEmpty()) {
            return AutomationDispatchOutcome.failed("Integração de WhatsApp desativada ou incompleta em Integrações", null, payload);
        }
        String to = context.cliente() == null ? null : context.cliente().whatsapp();
        if (to == null) {
            return AutomationDispatchOutcome.failed("AutomationRecipient sem WhatsApp válido", null, payload);
        }
        WhatsappSendResult result = whatsappPort.sendTemplate(credentials.get().phoneNumberId(),
                credentials.get().accessToken(), to, entrega.whatsappTemplate(), entrega.whatsappIdiomaOrDefault(),
                List.of(mensagem));
        return result.success()
                ? new AutomationDispatchOutcome(CampaignDispatchStatus.ENVIADO, null, null, payload)
                : AutomationDispatchOutcome.failed(truncate(result.error()), null, payload);
    }

    private static AutomationDispatchOutcome fromWebhook(WebhookDispatchResult result, Map<String, Object> payload) {
        return result.success()
                ? new AutomationDispatchOutcome(CampaignDispatchStatus.ENVIADO, null, result.statusCode(), payload)
                : AutomationDispatchOutcome.failed(result.errorMessage(), result.statusCode(), payload);
    }

    // ── Mensagem e payload ────────────────────────────────────────────────────

    /** Variáveis do template — todas, independentemente de {@code metadados} (que só filtra o payload). */
    private Map<String, Object> templateVariables(CampaignAutomation automation, AutomationDispatchContext context) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (context.cliente() != null) {
            AutomationRecipient c = context.cliente();
            Map<String, Object> cliente = new LinkedHashMap<>();
            cliente.put("nome", c.nome());
            cliente.put("primeiroNome", primeiroNome(c.nome()));
            cliente.put("contato", c.contato());
            cliente.put("whatsapp", c.whatsapp());
            cliente.put("email", c.email());
            cliente.put("cpf", c.cpf());
            cliente.put("origem", c.origem());
            cliente.put("segmento", SEGMENTO_PLACEHOLDER);
            cliente.put("estagio", c.estagio() == null ? null : ESTAGIO_LABEL.getOrDefault(c.estagio(), c.estagio().name()));
            cliente.put("cadastradoEm", c.cadastradoEm() == null ? null : DATA_PT_BR.format(c.cadastradoEm().atZone(ZONA_BRASIL)));
            cliente.put("ltv", formatMoeda(LTV_PLACEHOLDER));
            cliente.put("cashback", formatMoeda(c.cashback()));
            cliente.put("tags", String.join(", ", c.tags()));
            variables.put("cliente", cliente);
        }
        variables.put("loja", Map.of("nome", LOJA_NOME));
        ZonedDateTime agora = ZonedDateTime.now(ZONA_BRASIL);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hoje", DATA_PT_BR.format(agora));
        data.put("hora", HORA_PT_BR.format(agora));
        variables.put("data", data);
        variables.put("automacao", Map.of("nome", automation.nome(), "id", automation.id() == null ? 0L : automation.id()));
        if (context.order() != null) {
            Order o = context.order();
            Map<String, Object> pedido = new LinkedHashMap<>();
            pedido.put("numero", o.orderNumber());
            pedido.put("total", formatMoeda(o.netAmount()));
            pedido.put("status", o.status().name());
            variables.put("pedido", pedido);
        }
        if (!context.contexto().isEmpty()) {
            variables.put("contexto", context.contexto());
        }
        return variables;
    }

    /** Payload da seção 5.3 do contrato: só os blocos de {@code metadados}. */
    private Map<String, Object> payload(CampaignAutomation automation, AutomationDispatchContext context, String mensagem) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("evento", context.evento());
        payload.put("mensagem", mensagem);
        payload.put("canal", automation.canal().name());
        if (automation.includes(AutomationMetadata.CLIENTE) && context.cliente() != null) {
            AutomationRecipient c = context.cliente();
            Map<String, Object> cliente = new LinkedHashMap<>();
            cliente.put("id", c.id());
            cliente.put("nome", c.nome());
            cliente.put("contato", c.contato());
            cliente.put("whatsapp", c.whatsapp());
            cliente.put("email", c.email());
            cliente.put("cpf", c.cpf());
            cliente.put("segmento", SEGMENTO_PLACEHOLDER);
            cliente.put("estagio", c.estagio() == null ? null : c.estagio().name());
            cliente.put("ltv", LTV_PLACEHOLDER);
            cliente.put("cashback", c.cashback());
            cliente.put("tags", c.tags());
            payload.put("cliente", cliente);
        }
        if (automation.includes(AutomationMetadata.LOJA)) {
            payload.put("loja", Map.of("nome", LOJA_NOME));
        }
        if (automation.includes(AutomationMetadata.PEDIDO) && context.order() != null) {
            payload.put("pedido", pedidoPayload(context.order()));
        }
        Instant agora = Instant.now();
        if (automation.includes(AutomationMetadata.DATA)) {
            payload.put("data", Map.of("iso", agora.atZone(ZONA_BRASIL).toOffsetDateTime().toString()));
        }
        if (automation.includes(AutomationMetadata.AUTOMACAO)) {
            Map<String, Object> automacao = new LinkedHashMap<>();
            automacao.put("id", automation.id());
            automacao.put("nome", automation.nome());
            automacao.put("gatilho", automation.gatilho().name());
            automacao.put("canal", automation.canal().name());
            payload.put("automacao", automacao);
        }
        if (!context.contexto().isEmpty()) {
            payload.put("contexto", context.contexto());
        }
        payload.put("disparadoEm", agora.toString());
        payload.put("origem", WEBHOOK_ORIGEM);
        if (context.teste()) {
            payload.put("teste", true);
        }
        return payload;
    }

    private static Map<String, Object> pedidoPayload(Order order) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("id", order.id());
        pedido.put("numero", order.orderNumber());
        pedido.put("canal", order.channel().name());
        pedido.put("total", order.netAmount());
        pedido.put("status", order.status().name());
        pedido.put("itens", order.items().stream().map(item -> {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("sku", item.sku());
            i.put("nome", item.productName());
            i.put("quantidade", item.quantity());
            i.put("precoUnitario", item.unitPrice());
            return i;
        }).toList());
        return pedido;
    }

    // ── AutomationRecipient ───────────────────────────────────────────────────────────────

    private AutomationRecipient clienteOf(Customer customer) {
        BigDecimal cashback = cashbackUseCase.getCustomerBalance(customer.id()).available();
        List<String> tags = customerTagRepository.findTagsByCustomerId(customer.id()).stream().map(Tag::nome).toList();
        return new AutomationRecipient(customer.id(), customer.nome(), customer.contato(), toWhatsAppNumber(customer.contato()),
                customer.email(), customer.cpf(), customer.origem(), customer.estagio(), customer.cadastradoEm(),
                cashback, tags);
    }

    private static AutomationRecipient testCliente() {
        return new AutomationRecipient(0L, "AutomationRecipient de Teste", "5511999999999", "5511999999999", "teste@mahal.dev",
                "000.000.000-00", "teste", CustomerStage.NOVO_LEAD, Instant.now(), BigDecimal.ZERO, List.of());
    }

    /** Dígitos apenas, com DDI 55 — aproxima o {@code toWhatsAppNumber} do mahal-admin. */
    static String toWhatsAppNumber(String contato) {
        if (contato == null) {
            return null;
        }
        String digits = contato.replaceAll("\\D", "");
        if (digits.length() < 10 || digits.length() > 13) {
            return null;
        }
        return digits.startsWith("55") ? digits : "55" + digits;
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return nome;
        }
        int espaco = nome.indexOf(' ');
        return espaco < 0 ? nome : nome.substring(0, espaco);
    }

    private static String formatMoeda(BigDecimal valor) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.of("pt", "BR"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);
        return formatter.format(valor == null ? BigDecimal.ZERO : valor);
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
    }
}
