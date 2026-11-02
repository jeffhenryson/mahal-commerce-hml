package com.cernecommerce.core.domain.model.estoque;

/** Situação de uma promoção por quantidade num instante: derivada de {@code active} e do período. */
public enum PromotionStatus {
    ATIVA,
    AGENDADA,
    ENCERRADA,
    INATIVA
}
