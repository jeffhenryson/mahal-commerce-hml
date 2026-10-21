package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/** Perfil de sabor marcado para um SKU de essência (V169). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "salao_essencia_perfil")
@IdClass(SalaoEssenciaPerfilEntity.Id.class)
public class SalaoEssenciaPerfilEntity {

    @jakarta.persistence.Id
    @Column(name = "sku", length = 50)
    private String sku;

    @jakarta.persistence.Id
    @Column(name = "perfil", length = 20)
    private String perfil;

    public static class Id implements Serializable {
        private String sku;
        private String perfil;

        public Id() {}

        public Id(String sku, String perfil) {
            this.sku = sku;
            this.perfil = perfil;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(sku, id.sku) && Objects.equals(perfil, id.perfil);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sku, perfil);
        }
    }
}
