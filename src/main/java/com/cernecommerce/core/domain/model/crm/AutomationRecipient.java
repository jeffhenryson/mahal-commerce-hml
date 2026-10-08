package com.cernecommerce.core.domain.model.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Cliente destinatário de um disparo de automação, já com saldo de cashback e tags resolvidos. */
public record AutomationRecipient(Long id, String nome, String contato, String whatsapp, String email, String cpf,
        String origem, CustomerStage estagio, Instant cadastradoEm, BigDecimal cashback, List<String> tags) {
}
