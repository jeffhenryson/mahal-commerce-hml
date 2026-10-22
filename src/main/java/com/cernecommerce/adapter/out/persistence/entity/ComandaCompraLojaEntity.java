package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Venda da loja ligada à mesa (V171, PDV-F036). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "comanda_compra_loja")
@IdClass(ComandaCompraLojaEntity.Id.class)
public class ComandaCompraLojaEntity {

    @jakarta.persistence.Id
    @Column(name = "comanda_id")
    private Long comandaId;

    @jakarta.persistence.Id
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "registrado_por", nullable = false, length = 80)
    private String registradoPor;

    @Column(name = "registrado_em", nullable = false)
    private Instant registradoEm;

    public static class Id implements Serializable {
        private Long comandaId;
        private Long orderId;

        public Id() {}

        public Id(Long comandaId, Long orderId) {
            this.comandaId = comandaId;
            this.orderId = orderId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(comandaId, id.comandaId) && Objects.equals(orderId, id.orderId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(comandaId, orderId);
        }
    }
}
