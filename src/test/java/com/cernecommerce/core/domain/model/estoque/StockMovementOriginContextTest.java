package com.cernecommerce.core.domain.model.estoque;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockMovementOriginContextTest {

    @Test
    void foraDeEscopoNaoHaOrigemENaoGuardaIds() {
        StockMovementOriginContext.recordMovement(1L);

        assertThat(StockMovementOriginContext.current()).isNull();
    }

    @Test
    void escopoDeDentroVenceEAoFecharVoltaOdeFora() {
        try (var venda = StockMovementOriginContext.open(StockMovementOrigin.order(null))) {
            StockMovementOriginContext.recordMovement(1L);
            try (var embalagem = StockMovementOriginContext.open(
                    new StockMovementOrigin(StockMovementOriginType.PACKAGING, null))) {
                assertThat(StockMovementOriginContext.current().type()).isEqualTo(StockMovementOriginType.PACKAGING);
                StockMovementOriginContext.recordMovement(2L);
                assertThat(embalagem.movementIds()).containsExactly(2L);
            }
            assertThat(StockMovementOriginContext.current()).isEqualTo(StockMovementOrigin.order(null));
            StockMovementOriginContext.recordMovement(3L);
            // A linha da embalagem não é da venda: não pode ser recarimbada com o pedido depois.
            assertThat(venda.movementIds()).containsExactly(1L, 3L);
        }
        assertThat(StockMovementOriginContext.current()).isNull();
    }
}
