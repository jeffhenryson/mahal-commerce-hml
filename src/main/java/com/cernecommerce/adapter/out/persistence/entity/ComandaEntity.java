package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Comanda de mesa (PDV-F009). Tabela {@code comanda}.
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(name = "comanda")
public class ComandaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** Depósito da sessão que abriu a comanda (PDV-C004). */
    @Column(name = "warehouse_code", nullable = false, length = 50)
    private String warehouseCode;

    @Column(name = "table_or_customer_label", nullable = false, length = 100)
    private String tableOrCustomerLabel;

    @Column(nullable = false, length = 20)
    private String status;

    /** Preenchido só no fechamento. Nulo em ABERTA e em CANCELADA. */
    @Column(name = "order_id")
    private Long orderId;

    // PDV-F010 — cliente do CRM vinculado na abertura. Distinto de table_or_customer_label, que
    // é texto livre para achar a mesa na tela e nunca foi vínculo de cadastro.
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "opened_by", nullable = false, length = 80)
    private String openedBy;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    // PDV-F029 — quem encerrou (fechou, finalizou ou cancelou) e o motivo do cancelamento (V138).
    // Gravados por ComandaRepository.recordClosing, fora do record de domínio: o save da comanda
    // carrega a entidade existente e não toca nestes campos.
    @Column(name = "closed_by", length = 80)
    private String closedBy;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    // @OrderBy porque a comanda é lida na ordem de lançamento — é assim que a tela mostra a
    // sessão antes das trocas que se penduram nela, e sem isso a ordem de um bag fica a critério
    // do banco.
    @OneToMany(mappedBy = "comanda", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @ToString.Exclude
    private List<ComandaItemEntity> items = new ArrayList<>();
}
