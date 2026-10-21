package com.cernecommerce.core.domain.model.salao;

/** Gravado. O chamado esquecido não tem status próprio: é o ABERTO vencido ({@link ChamadoMesa#expiradoEm}). */
public enum StatusChamado {
    ABERTO,
    ATENDIDO
}
