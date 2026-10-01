package com.cernecommerce.core.domain.exception.recebivel;

import java.time.LocalDate;

/** Vencimento do marcado ausente ou no passado. */
public class InvalidDueDateException extends RuntimeException {

    public InvalidDueDateException(LocalDate dueDate, LocalDate today) {
        super(dueDate == null ? "Informe o vencimento (dueDate) da linha MARCADO"
                : "O vencimento " + dueDate + " não pode ser antes de hoje (" + today + ")");
    }
}
