package com.cernecommerce.core.domain.model.ecommerce;

import com.cernecommerce.core.domain.exception.ecommerce.RecusaInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MotivoRecusaTest {

    @Test
    void descrever_rotuloSozinhoOuComANota() {
        assertThat(MotivoRecusa.SEM_ESTOQUE.descrever(null)).isEqualTo("Sem estoque");
        assertThat(MotivoRecusa.SEM_ESTOQUE.descrever("   ")).isEqualTo("Sem estoque");
        assertThat(MotivoRecusa.FORA_DA_AREA.descrever(" Jardim Paraíso ")).isEqualTo(
                "Endereço fora da área de entrega: Jardim Paraíso");
    }

    @Test
    void descrever_outroEhANotaEElaEhObrigatoria() {
        assertThat(MotivoRecusa.OUTRO.descrever("Moto quebrou")).isEqualTo("Moto quebrou");
        assertThatThrownBy(() -> MotivoRecusa.OUTRO.descrever(" ")).isInstanceOf(RecusaInvalidaException.class);
    }

    @Test
    void descrever_notaAcimaDoTetoRecusa() {
        assertThatThrownBy(() -> MotivoRecusa.SEM_ESTOQUE.descrever("x".repeat(MotivoRecusa.NOTA_MAX + 1)))
                .isInstanceOf(RecusaInvalidaException.class);
    }
}
