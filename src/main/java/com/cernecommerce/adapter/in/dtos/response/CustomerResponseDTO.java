package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
public class CustomerResponseDTO {
    private Long id;
    private String nome;
    private String contato;
    private String email;
    private String cpf;
    private String origem;
    private Instant cadastradoEm;

    // Estágio manual do Kanban de atendimento (crm/kanban-segmentacao) — valor real, persistido.
    private CustomerStage estagio;

    // Placeholder até os domínios de pedidos e cashback existirem (ver crm/listagem-clientes-rfm) —
    // sempre 0/"NOVO" por enquanto, não refletem dados reais de compra.
    // "segmento" (RFM) é conceito distinto de "estagio" (Kanban manual) — não confundir.
    private BigDecimal ltv;
    private BigDecimal cashback;
    private String segmento;

    // Tags reais (crm/tags-segmentos) em POST/GET /crm/customers/{id} e no retorno de PATCH .../estagio.
    // Na listagem paginada (GET /crm/customers) vem sempre [] para evitar N+1 (1 query de tags por linha).
    private List<String> tags;

    @Schema(description = "CRM-F010 — limite de crédito efetivo do \"Marcar\" (individual ou o padrão da loja). "
            + "Só no GET por id.")
    private BigDecimal creditLimit;

    @Schema(description = "CRM-F010 — saldo marcado em aberto. Só no GET por id.")
    private BigDecimal openBalance;

    @Schema(description = "CRM-F010 — parte do saldo em aberto já vencida. Só no GET por id.")
    private BigDecimal overdueBalance;
}
