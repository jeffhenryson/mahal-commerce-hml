package com.cernecommerce.core.domain.model.pdv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** PDV-F047 — atividade do operador na sessão de caixa. */
class CashSessionActivityTest {

    @Test
    void notaEmBrancoViraNula() {
        CashSessionActivity activity = CashSessionActivity.register(1L, CashSessionActivityType.VARRER_LOJA, "  ",
                "caixa1");

        assertThat(activity.note()).isNull();
    }

    @Test
    void outroExigeNota() {
        assertThatThrownBy(() -> CashSessionActivity.register(1L, CashSessionActivityType.OUTRO, null, "caixa1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void notaTemNoMaximo280Caracteres() {
        assertThat(CashSessionActivity.register(1L, CashSessionActivityType.OUTRO, "x".repeat(280), "caixa1")
                .note()).hasSize(280);
        assertThatThrownBy(() -> CashSessionActivity.register(1L, CashSessionActivityType.OUTRO, "x".repeat(281),
                "caixa1")).isInstanceOf(IllegalArgumentException.class);
    }
}
