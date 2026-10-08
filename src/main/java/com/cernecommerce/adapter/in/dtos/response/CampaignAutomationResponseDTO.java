package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.AutomationMetadata;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CampaignAutomationResponseDTO {
    private Long id;
    private String nome;
    private CampaignTrigger gatilho;
    private AutomationEvent evento;
    private CustomerStage segmentoAlvo;
    private CampaignChannel canal;
    private String template;
    private boolean ativa;
    private Instant criadoEm;
    private AutomationDestination destino;

    /** {@code webhookHeaders} não é exposto aqui — é segredo; só {@code authLast4}. */
    private String webhookUrl;
    private String workflowPath;
    private String whatsappTemplate;
    private String whatsappIdioma;
    private AutomationAuthType authTipo;
    private String authHeaderNome;
    @Schema(description = "4 últimos caracteres do segredo de autenticação salvo; null = sem segredo.")
    private String authLast4;
    private List<AutomationMetadata> metadados;
}
