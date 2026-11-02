package com.cernecommerce.adapter.in.dtos.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.cernecommerce.core.domain.model.crm.CustomerStage;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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

    @Schema(description = "CRM-F012 — data de nascimento (yyyy-MM-dd), se informada.", example = "1990-05-17")
    private LocalDate dataNascimento;

    @Schema(description = "Pendência 8 — soma do líquido dos pedidos pagos (PAGO, SEPARADO, ENVIADO, ENTREGUE, "
            + "CONCLUIDO, RESERVADO). Real na lista e no GET por id; nulo só em respostas de escrita (POST/PUT/PATCH), "
            + "que não recalculam.", nullable = true)
    private BigDecimal ltv;

    @Schema(description = "Pendência 8 — saldo de cashback disponível agora. Real na lista e no GET por id; nulo nas "
            + "respostas de escrita.", nullable = true)
    private BigDecimal cashback;

    @Schema(description = "V183 — segmento: Novo, Recorrente, VIP, Em Risco ou Inativo. Recalculado todo dia e ao "
            + "mexer na tag VIP; congelado quando `segmentoManual`. Não confundir com `estagio` (funil manual).",
            example = "Recorrente")
    private String segmento;

    @Schema(description = "V183 — true quando o segmento foi escolhido à mão (PATCH /crm/customers/{id}/segmento).")
    private boolean segmentoManual;

    @Schema(description = "V183 — cidade, opcional.", nullable = true)
    private String cidade;

    @Schema(description = "V183 — URL da foto do cliente.", nullable = true)
    private String avatarUrl;

    @Schema(description = "Pendência 8 — quantidade de pedidos pagos.", nullable = true)
    private Long pedidosCount;

    @Schema(description = "Pendência 8 — LTV dividido pelos pedidos pagos.", nullable = true)
    private BigDecimal ticketMedio;

    @Schema(description = "Pendência 8 — data do último pedido pago; nulo se nunca comprou.", nullable = true)
    private Instant ultimaCompraEm;

    @Schema(description = "Nomes das tags. Reais na lista (em lote, sem N+1), no GET por id e no PATCH de estágio.")
    private List<String> tags;

    @Schema(description = "CRM-F010 — limite de crédito do \"Marcar\": o teto total do cliente, se houver; senão, "
            + "a soma dos limites efetivos de balcão e mesa (CRM-F011). O detalhe por canal está em "
            + "on-account-eligibility.limitsByChannel. Só no GET por id.")
    private BigDecimal creditLimit;

    @Schema(description = "CRM-F010 — saldo marcado em aberto. Só no GET por id.")
    private BigDecimal openBalance;

    @Schema(description = "CRM-F010 — parte do saldo em aberto já vencida. Só no GET por id.")
    private BigDecimal overdueBalance;
}
