package com.cernecommerce.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/** Configuração do cardápio de sessão, linha única id = 1 (PDV-F021, V128). */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "session_settings")
public class SessionSettingsEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "vaso_padrao_codigo", length = 30)
    private String vasoPadraoCodigo;

    @Column(name = "vaso_grande_codigo", length = 30)
    private String vasoGrandeCodigo;

    @Column(name = "upgrade_vaso_grande_preco", nullable = false, precision = 12, scale = 2)
    private BigDecimal upgradeVasoGrandePreco;

    @Column(name = "dias_duplo_rosh", nullable = false, length = 80)
    private String diasDuploRosh;

    @Column(name = "upgrade_carvao_jumbo_preco", nullable = false, precision = 12, scale = 2)
    private BigDecimal upgradeCarvaoJumboPreco;
}
