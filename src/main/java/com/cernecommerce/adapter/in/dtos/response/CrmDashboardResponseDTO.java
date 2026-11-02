package com.cernecommerce.adapter.in.dtos.response;

import com.cernecommerce.core.domain.model.crm.CustomerStage;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class CrmDashboardResponseDTO {
    private long totalClientes;

    // Ativo = estagio != INATIVO (decisão de escopo, ver crm/dashboard-overview).
    private long clientesAtivos;

    // Placeholder até os domínios de pedidos/cashback e de campanhas existirem.
    private BigDecimal ltvMedio;
    private long disparosWhatsappMes;
    private Map<String, Long> porSegmento;

    // Dado real, calculado sobre o campo estagio do Kanban.
    private Map<CustomerStage, Long> porEstagio;

    @io.swagger.v3.oas.annotations.media.Schema(description = "Pendência 8 — saldo de cashback disponível da base "
            + "inteira: o que a loja deve aos clientes.")
    private BigDecimal cashbackEmAberto;
}
