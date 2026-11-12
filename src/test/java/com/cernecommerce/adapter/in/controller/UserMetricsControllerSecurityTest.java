package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.CrmUseCase;
import com.cernecommerce.core.ports.in.UserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil > métricas: a forma {@code me} só exige estar autenticado; a forma {@code {id}} exige
 * {@code USER_READ} + a permissão do assunto (403 antes do 404).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class UserMetricsControllerSecurityTest {

    private static final String PERIODO = "from=2026-11-01T03:00:00.000Z&to=2026-11-08T02:59:59.999Z";
    private static final String PERIODO_LONGO = "from=2025-01-01T03:00:00.000Z&to=2026-11-08T02:59:59.999Z";

    @Autowired private WebApplicationContext context;
    @Autowired private UserUseCase userUseCase;
    @Autowired private CrmUseCase crmUseCase;

    private MockMvc mockMvc;
    private User alvo;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        alvo = userUseCase.createUser("atendente-" + UUID.randomUUID().toString().substring(0, 8), "Senha@123",
                List.of());
    }

    private static RequestPostProcessor como(String username, String... permissoes) {
        return user(username).authorities(Arrays.stream(permissoes).map(SimpleGrantedAuthority::new).toList());
    }

    /** subpath com query, permissão do assunto na forma {id}. */
    static Stream<Arguments> endpoints() {
        return Stream.of(
                Arguments.of("performance?" + PERIODO, "ORDER_READ"),
                Arguments.of("performance?" + PERIODO, "PDV_READ"),
                Arguments.of("cash-sessions?page=0&size=5", "PDV_SESSION_READ_ANY"),
                Arguments.of("access-log?page=0&size=20", "AUDIT_READ"),
                Arguments.of("attendance?" + PERIODO, "AUDIT_READ"));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void formaMe_semPermissaoNenhuma_responde200(String subpath, String ignored) throws Exception {
        mockMvc.perform(get("/users/me/" + subpath).with(como(alvo.getUsername())))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void formaId_comPermissoes_responde200(String subpath, String permissao) throws Exception {
        mockMvc.perform(get("/users/" + alvo.getId() + "/" + subpath).with(como("gerente", "USER_READ", permissao)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void formaId_semPermissaoDoAssunto_responde403(String subpath, String permissao) throws Exception {
        mockMvc.perform(get("/users/" + alvo.getId() + "/" + subpath).with(como("gerente", "USER_READ")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/users/" + alvo.getId() + "/" + subpath).with(como("gerente", permissao)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void formaId_usuarioInexistente_responde404(String subpath, String permissao) throws Exception {
        mockMvc.perform(get("/users/999999999/" + subpath).with(como("gerente", "USER_READ", permissao)))
                .andExpect(status().isNotFound());
    }

    @Test
    void performance_devolveOsTresBlocosZerados() throws Exception {
        mockMvc.perform(get("/users/me/performance?" + PERIODO).with(como(alvo.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdv.revenue").isNumber())
                .andExpect(jsonPath("$.pdv.orders").value(0))
                .andExpect(jsonPath("$.cash.opened").value(0))
                .andExpect(jsonPath("$.tables.itemsLaunched").value(0));
    }

    /** PDV_READ é do atendente: não basta para ver o caixa de outra pessoa (V163). */
    @Test
    void cashSessionsDeOutro_comPdvReadApenas_responde403() throws Exception {
        mockMvc.perform(get("/users/" + alvo.getId() + "/cash-sessions").with(como("gerente", "USER_READ", "PDV_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void periodoAcimaDe366Dias_responde400() throws Exception {
        mockMvc.perform(get("/users/me/performance?" + PERIODO_LONGO).with(como(alvo.getUsername())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/users/me/attendance?" + PERIODO_LONGO).with(como(alvo.getUsername())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerOrders_semClienteVinculado_responde404() throws Exception {
        mockMvc.perform(get("/users/me/customer-orders?" + PERIODO).with(como(alvo.getUsername())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_CUSTOMER_NOT_LINKED"));
        mockMvc.perform(get("/users/" + alvo.getId() + "/customer-orders?" + PERIODO)
                        .with(como("gerente", "USER_READ", "ORDER_READ")))
                .andExpect(status().isNotFound());
    }

    @Test
    void customerOrders_comClienteVinculado_respondeResumoEPagina() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Long customerId = crmUseCase.createCustomer("Cliente " + suffix,
                "1197" + String.format("%07d", Math.abs(suffix.hashCode()) % 10_000_000), null, null, "PDV").id();
        userUseCase.linkCustomer(alvo.getId(), customerId);

        mockMvc.perform(get("/users/me/customer-orders?" + PERIODO).with(como(alvo.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orders").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(0));
        mockMvc.perform(get("/users/" + alvo.getId() + "/customer-orders?" + PERIODO)
                        .with(como("gerente", "USER_READ")))
                .andExpect(status().isForbidden());

        // /users/me passa a trazer o vínculo, que é o que liga a aba Compras.
        mockMvc.perform(get("/users/me").with(como(alvo.getUsername())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(customerId))
                .andExpect(jsonPath("$.customerName").value("Cliente " + suffix));
    }
}
