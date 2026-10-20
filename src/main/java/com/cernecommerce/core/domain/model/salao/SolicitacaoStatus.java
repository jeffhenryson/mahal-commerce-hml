package com.cernecommerce.core.domain.model.salao;

/**
 * Status da solicitação de sessão pelo QR. {@link #EXPIRADA} não é gravado: é a leitura de uma
 * {@link #PENDENTE} esquecida há mais de {@link SolicitacaoSessao#VALIDADE} — sem job, e sem o
 * atendente poder aceitar um pedido de quem talvez já foi embora.
 */
public enum SolicitacaoStatus {
    PENDENTE,
    ACEITA,
    RECUSADA,
    EXPIRADA
}
