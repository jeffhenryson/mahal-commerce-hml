package com.cernecommerce.core.domain.model.pedido;

import com.cernecommerce.core.domain.exception.pedido.InvalidDeliveryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pendência 10 §2 — coordenadas do endereço de entrega. */
class DeliveryAddressCoordinatesTest {

    private static DeliveryAddress endereco(Double lat, Double lng) {
        return new DeliveryAddress("Rua das Flores", "12", null, "24220-000", "Icaraí", "Niterói", "RJ", "BR", null,
                lat, lng);
    }

    @Test
    void aceitaAsDuasOuNenhuma() {
        assertThat(endereco(-22.9035, -43.1092).latitude()).isEqualTo(-22.9035);
        assertThat(endereco(null, null).latitude()).isNull();
        assertThatThrownBy(() -> endereco(-22.9, null)).isInstanceOf(InvalidDeliveryException.class);
    }

    @Test
    void recusaForaDoGlobo() {
        assertThatThrownBy(() -> endereco(-91.0, 0.0)).isInstanceOf(InvalidDeliveryException.class);
        assertThatThrownBy(() -> endereco(0.0, 181.0)).isInstanceOf(InvalidDeliveryException.class);
    }

    @Test
    void mudarDeLugarSemCoordenadasNovasApagaAsAntigas() {
        DeliveryAddress original = endereco(-22.9035, -43.1092);

        DeliveryAddress outroNumero = original.merge(new DeliveryAddress.Patch(null, "99", null, null, null, null,
                null, null, null));
        DeliveryAddress soReferencia = original.merge(new DeliveryAddress.Patch(null, null, null, null, null, null,
                null, null, "Portão azul"));
        DeliveryAddress comNovas = original.merge(new DeliveryAddress.Patch(null, "99", null, null, null, null,
                null, null, null, -22.91, -43.11));

        assertThat(outroNumero.latitude()).isNull();
        assertThat(soReferencia.latitude()).isEqualTo(-22.9035);
        assertThat(comNovas.latitude()).isEqualTo(-22.91);
    }
}
