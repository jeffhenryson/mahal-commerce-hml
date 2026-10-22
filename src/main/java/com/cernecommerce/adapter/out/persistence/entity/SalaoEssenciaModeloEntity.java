package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/** Essência do modelo semanal do Salão (V171): 1 = segunda. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "salao_essencia_modelo")
@IdClass(SalaoEssenciaModeloEntity.Id.class)
public class SalaoEssenciaModeloEntity {

    @jakarta.persistence.Id
    @Column(name = "dia_semana")
    private Integer diaSemana;

    @jakarta.persistence.Id
    @Column(name = "sku", length = 50)
    private String sku;

    public static class Id implements Serializable {
        private Integer diaSemana;
        private String sku;

        public Id() {}

        public Id(Integer diaSemana, String sku) {
            this.diaSemana = diaSemana;
            this.sku = sku;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(diaSemana, id.diaSemana) && Objects.equals(sku, id.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(diaSemana, sku);
        }
    }
}
