package com.cernecommerce.adapter.out.persistence.entity;

import com.cernecommerce.core.domain.model.crm.CampaignDispatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// (automation_id, event_key) único: no máximo um disparo por automação + ocorrência. event_key é
// nulo no disparo manual, e nulos não colidem.
@Table(name = "campaign_log", uniqueConstraints = @UniqueConstraint(
        name = "uk_campaign_log_automation_event", columnNames = {"automation_id", "event_key"}))
public class CampaignLogEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "automation_id", nullable = false)
    private Long automationId;

    /** Nulo nos eventos da loja (caixa fechado, estoque baixo), que não têm cliente destinatário. */
    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CampaignDispatchStatus status;

    @Column(name = "disparado_em", nullable = false)
    private Instant disparadoEm;

    @Column(name = "convertido_em")
    private Instant convertidoEm;

    @Column(name = "erro_detalhe", length = 500)
    private String erroDetalhe;

    @Column(name = "event_key", length = 160)
    private String eventKey;
}
