package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.crm.AutomationAuthType;
import com.cernecommerce.core.domain.model.crm.AutomationDestination;
import com.cernecommerce.core.domain.model.crm.AutomationEvent;
import com.cernecommerce.core.domain.model.crm.CampaignChannel;
import com.cernecommerce.core.domain.model.crm.CampaignTrigger;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "campaign_automations")
public class CampaignAutomationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CampaignTrigger gatilho;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AutomationEvent evento;

    /** Nulo só no gatilho EVENTO (= qualquer estágio). */
    @Enumerated(EnumType.STRING)
    @Column(name = "segmento_alvo", length = 20)
    private CustomerStage segmentoAlvo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CampaignChannel canal;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String template;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /**
     * {@code Map<String,String>} serializado em JSON e cifrado (AES-GCM, {@code SecretCipherPort}) —
     * guarda o segredo de autenticação do webhook. Linhas anteriores à V144 ainda podem trazer o
     * JSON em claro; são recifradas no próximo save.
     */
    @Column(name = "webhook_headers", columnDefinition = "TEXT")
    private String webhookHeaders;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AutomationDestination destino = AutomationDestination.WEBHOOK;

    @Column(name = "workflow_path", length = 200)
    private String workflowPath;

    @Column(name = "whatsapp_template", length = 512)
    private String whatsappTemplate;

    @Column(name = "whatsapp_idioma", length = 10)
    private String whatsappIdioma;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_tipo", nullable = false, length = 10)
    private AutomationAuthType authTipo = AutomationAuthType.NONE;

    @Column(name = "auth_header_nome", length = 100)
    private String authHeaderNome;

    @Column(name = "auth_last4", length = 4)
    private String authLast4;

    /** Blocos do payload ({@code AutomationMetadata}) separados por vírgula. */
    @Column(length = 100)
    private String metadados;
}
