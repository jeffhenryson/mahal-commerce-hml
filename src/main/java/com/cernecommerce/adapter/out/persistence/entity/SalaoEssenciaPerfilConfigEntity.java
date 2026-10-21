package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** SKU cujos perfis a casa já marcou (V169) — sem esta linha, o QR sugere pelo nome. */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "salao_essencia_perfil_config")
public class SalaoEssenciaPerfilConfigEntity {

    @Id
    @Column(name = "sku", length = 50)
    private String sku;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}
