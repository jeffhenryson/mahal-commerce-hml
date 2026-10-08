package com.cernecommerce.core.domain.model.crm;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Regra de automação/campanha do CRM: quando dispara ({@link #gatilho}, {@link #evento},
 * {@link #segmentoAlvo}), o que diz ({@link #template}, renderizado por cliente) e para onde vai
 * ({@link #entrega}).
 *
 * <p>{@code segmentoAlvo} é obrigatório nos gatilhos {@link CampaignTrigger#MANUAL} (é o público do
 * disparo em lote) e {@link CampaignTrigger#ENTRADA_ESTAGIO} (é o estágio que dispara). Em
 * {@link CampaignTrigger#EVENTO} é um filtro opcional: {@code null} = qualquer estágio.</p>
 *
 * <p>Uma automação de webhook sem {@code webhookUrl} (formato legado) não envia nada: o disparo só
 * registra {@link CampaignLogEntry} em {@code PENDENTE_INTEGRACAO}.</p>
 */
public record CampaignAutomation(
    Long id,
    String nome,
    CampaignTrigger gatilho,
    AutomationEvent evento,
    CustomerStage segmentoAlvo,
    CampaignChannel canal,
    String template,
    boolean ativa,
    Instant criadoEm,
    AutomationDelivery entrega,
    List<AutomationMetadata> metadados
) {

    public CampaignAutomation {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome é obrigatório");
        }
        if (gatilho == null) {
            throw new IllegalArgumentException("gatilho é obrigatório");
        }
        if (gatilho == CampaignTrigger.EVENTO) {
            if (evento == null) {
                throw new IllegalArgumentException("evento é obrigatório quando o gatilho é EVENTO");
            }
        } else {
            evento = null;
            if (segmentoAlvo == null) {
                throw new IllegalArgumentException("segmentoAlvo é obrigatório para o gatilho " + gatilho);
            }
        }
        if (canal == null) {
            throw new IllegalArgumentException("canal é obrigatório");
        }
        if (template == null || template.isBlank()) {
            throw new IllegalArgumentException("template é obrigatório");
        }
        entrega = entrega == null ? AutomationDelivery.webhook(null, null) : entrega;
        metadados = normalize(metadados);
    }

    /** Cria uma nova automação (sem id, ativa por padrão, criadoEm no momento atual, sem webhook). */
    public static CampaignAutomation create(String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template) {
        return new CampaignAutomation(null, nome, gatilho, null, segmentoAlvo, canal, template, true, Instant.now(),
                null, null);
    }

    /** Reconstitui uma automação de webhook próprio (formato anterior aos destinos). */
    public static CampaignAutomation of(Long id, String nome, CampaignTrigger gatilho, CustomerStage segmentoAlvo,
            CampaignChannel canal, String template, boolean ativa, Instant criadoEm, String webhookUrl,
            Map<String, String> webhookHeaders) {
        return new CampaignAutomation(id, nome, gatilho, null, segmentoAlvo, canal, template, ativa, criadoEm,
                AutomationDelivery.webhook(webhookUrl, webhookHeaders), null);
    }

    /** Retorna uma cópia desta automação com a flag ativa/inativa atualizada. */
    public CampaignAutomation withAtiva(boolean novaAtiva) {
        return new CampaignAutomation(id, nome, gatilho, evento, segmentoAlvo, canal, template, novaAtiva, criadoEm,
                entrega, metadados);
    }

    /** Retorna uma cópia com todos os campos editáveis atualizados (PUT). Mantém id, ativa e criadoEm. */
    public CampaignAutomation withDetails(String novoNome, CampaignTrigger novoGatilho, AutomationEvent novoEvento,
            CustomerStage novoSegmentoAlvo, CampaignChannel novoCanal, String novoTemplate,
            AutomationDelivery novaEntrega, List<AutomationMetadata> novosMetadados) {
        return new CampaignAutomation(id, novoNome, novoGatilho, novoEvento, novoSegmentoAlvo, novoCanal, novoTemplate,
                ativa, criadoEm, novaEntrega, novosMetadados);
    }

    public AutomationDestination destino() {
        return entrega.destino();
    }

    public String webhookUrl() {
        return entrega.webhookUrl();
    }

    public Map<String, String> webhookHeaders() {
        return entrega.webhookHeaders();
    }

    /** Webhook próprio configurado — o destino WEBHOOK com URL. */
    public boolean hasWebhook() {
        return entrega.destino() == AutomationDestination.WEBHOOK && entrega.webhookUrl() != null;
    }

    /** Se o segmento deixa este estágio passar — sem segmento (só em EVENTO), qualquer um passa. */
    public boolean acceptsStage(CustomerStage estagio) {
        return segmentoAlvo == null || segmentoAlvo == estagio;
    }

    public boolean includes(AutomationMetadata bloco) {
        return metadados.contains(bloco);
    }

    private static List<AutomationMetadata> normalize(List<AutomationMetadata> metadados) {
        if (metadados == null || metadados.isEmpty()) {
            return AutomationMetadata.DEFAULT;
        }
        EnumSet<AutomationMetadata> set = EnumSet.noneOf(AutomationMetadata.class);
        metadados.stream().filter(m -> m != null).forEach(set::add);
        return set.isEmpty() ? AutomationMetadata.DEFAULT : List.copyOf(set);
    }
}
