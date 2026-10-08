package com.cernecommerce.adapter.in.dtos.request;

import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** Cria uma automação (POST /crm/automacoes). */
@Data
public class CampaignAutomationRequest {
    @NotBlank
    @Size(max = 100)
    private String nome;

    @NotNull
    private CampaignTrigger gatilho;

    @Schema(description = "Obrigatório quando gatilho = EVENTO.")
    private AutomationEvent evento;

    @Schema(description = "Obrigatório em MANUAL e ENTRADA_ESTAGIO; em EVENTO é filtro opcional (null = qualquer estágio).")
    private CustomerStage segmentoAlvo;

    @NotNull
    private CampaignChannel canal;

    @NotBlank
    @Size(max = 2000)
    private String template;

    @Schema(description = "WEBHOOK (padrão), PLATAFORMA ou WHATSAPP_META.")
    private AutomationDestination destino;

    @Size(max = 500)
    @Schema(description = "Obrigatório no destino WEBHOOK.")
    private String webhookUrl;

    @Size(max = 200)
    @Schema(example = "reativacao-60-dias", description = "Obrigatório no destino PLATAFORMA: vai em {baseUrl}/{workflowPath}.")
    private String workflowPath;

    @Size(max = 512)
    @Schema(example = "promo_cashback", description = "Obrigatório no destino WHATSAPP_META: template aprovado na Meta.")
    private String whatsappTemplate;

    @Size(max = 10)
    @Schema(example = "pt_BR", description = "Idioma do template; ausente = pt_BR.")
    private String whatsappIdioma;

    @Schema(description = "Autenticação do webhook próprio: NONE (padrão), BEARER ou HEADER.")
    private AutomationAuthType authTipo;

    @Size(max = 100)
    @Schema(example = "X-Api-Key", description = "Nome do header quando authTipo = HEADER.")
    private String authHeaderNome;

    @Size(max = 5)
    @Schema(description = "Segredo de autenticação, cifrado em repouso e nunca devolvido. Ausente mantém o salvo; {} remove.")
    private Map<@Size(max = 100) String, @Size(max = 2000) String> webhookHeaders;

    @Schema(description = "Blocos do payload; ausente = [CLIENTE, LOJA, DATA, AUTOMACAO].")
    private List<AutomationMetadata> metadados;

    @Override
    public String toString() {
        return "CampaignAutomationRequest(nome=" + nome + ", gatilho=" + gatilho + ", evento=" + evento + ", destino=" + destino
                + ", authTipo=" + authTipo + ", webhookHeaders=" + (webhookHeaders == null ? "null" : "***") + ")";
    }
}
