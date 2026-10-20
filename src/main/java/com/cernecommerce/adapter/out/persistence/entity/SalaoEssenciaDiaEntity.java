package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** Essência marcada como "do dia" no Salão (V167). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "salao_essencia_dia")
@IdClass(SalaoEssenciaDiaEntity.Id.class)
public class SalaoEssenciaDiaEntity {

    @jakarta.persistence.Id
    @Column(name = "data")
    private LocalDate data;

    @jakarta.persistence.Id
    @Column(name = "sku", length = 50)
    private String sku;

    public static class Id implements Serializable {
        private LocalDate data;
        private String sku;

        public Id() {}

        public Id(LocalDate data, String sku) {
            this.data = data;
            this.sku = sku;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(data, id.data) && Objects.equals(sku, id.sku);
        }

        @Override
        public int hashCode() {
            return Objects.hash(data, sku);
        }
    }
}
