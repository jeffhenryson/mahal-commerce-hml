package com.cernecommerce.core.domain.model.estoque;

/**
 * Severidade de um alerta de reposição de estoque. Ver {@link ReorderPoint#severityFor}.
 *
 * <p>{@link #NO_LIMITE}: saldo exatamente no mínimo — o produto já entra no alerta ao atingir o
 * limite, não só depois de passar dele. {@link #ABAIXO}: abaixo do mínimo, acima do crítico
 * (antigo {@code ATENCAO}). {@link #CRITICO}: no crítico ou abaixo.</p>
 */
public enum AlertSeverity {
    OK,
    NO_LIMITE,
    ABAIXO,
    CRITICO
}
