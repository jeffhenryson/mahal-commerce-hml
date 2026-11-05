package com.cernecommerce.core.domain.model.rbac;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleLabelsTest {

    @Test
    void papel_conhecido_tem_nome_proprio() {
        assertThat(RoleLabels.of("ROLE_ADMIN")).isEqualTo("Administrador");
    }

    @Test
    void papel_criado_pela_loja_perde_o_prefixo() {
        assertThat(RoleLabels.of("ROLE_CAIXA_CHEFE")).isEqualTo("Caixa chefe");
    }

    @Test
    void junta_os_papeis_em_ordem_alfabetica() {
        assertThat(RoleLabels.join(List.of(new Role("ROLE_DEV"), new Role("ROLE_ATENDENTE"), new Role("ROLE_ADMIN"))))
                .isEqualTo("Administrador, Atendente e Desenvolvedor");
    }

    @Test
    void sem_papel_nao_ha_texto() {
        assertThat(RoleLabels.join(List.of())).isNull();
    }
}
