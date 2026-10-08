package com.cernecommerce.adapter.in.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.core.domain.exception.user.EmailAlreadyExistsException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.infra.security.support.EmailVerificationTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o fluxo de verificação de e-mail:
 * cadastro (via serviço — o auto-cadastro HTTP está desativado) → POST /auth/verify-email → POST /auth/login
 *
 * Também cobre os casos de erro:
 * - POST /auth/register sempre responde 403 REGISTRATION_DISABLED
 * - login antes de verificar email deve retornar 401 (conta desabilitada)
 * - código inválido/expirado deve retornar 400
 * - cadastro duplicado é recusado
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuthRegistrationFlowIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EmailVerificationTestHelper verificationHelper;

    @Autowired
    private LoginRateLimiterPort rateLimiter;

    @Autowired
    private UserUseCase userUseCase;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void resetRateLimiter() {
        // Reseta estado acumulado de outros testes no mesmo contexto Spring.
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void register_then_verify_then_login_completes_successfully() throws Exception {
        MockMvc mvc = mockMvc();
        String username = "reguser_" + System.currentTimeMillis();
        String email = username + "@test.com";
        String password = "Secure@123";

        // 1. Cadastro — a conta fica desabilitada até a verificação
        userUseCase.registerUser(username, password, email, List.of());

        // 2. Login antes de verificar email — deve retornar 401 (sem enumeração: desabilitado = credenciais inválidas)
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isUnauthorized());

        // 3. Buscar código de verificação diretamente no banco (sem envio de email real em dev)
        String code = verificationHelper.getCodeForUsername(username);

        // 4. Verificar email com código correto — deve retornar 204
        mvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        // 5. Login após verificação — deve retornar 200 com tokens
        MvcResult result = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = om.readTree(result.getResponse().getContentAsString());
        assert json.has("accessToken") : "Resposta deve conter accessToken";
        assert json.has("refreshToken") : "Resposta deve conter refreshToken";
    }

    @Test
    void verify_with_invalid_code_returns_400() throws Exception {
        MockMvc mvc = mockMvc();

        mvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_endpoint_returns_403_registrationDisabled() throws Exception {
        mockMvc().perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"selfsignup\",\"password\":\"Secure@123\",\"email\":\"self@test.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REGISTRATION_DISABLED"));
    }

    @Test
    void register_duplicate_username_is_rejected() {
        String username = "dupuser_" + System.currentTimeMillis();
        userUseCase.registerUser(username, "Secure@123", username + "@test.com", List.of());

        assertThatThrownBy(() -> userUseCase.registerUser(username, "Secure@123", "other_" + username + "@test.com", List.of()))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void register_duplicate_email_is_rejected() {
        String ts = String.valueOf(System.currentTimeMillis());
        String email = "dup_" + ts + "@test.com";
        userUseCase.registerUser("u1_" + ts, "Secure@123", email, List.of());

        assertThatThrownBy(() -> userUseCase.registerUser("u2_" + ts, "Secure@123", email, List.of()))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void resend_verification_always_returns_204_regardless_of_email_existence() throws Exception {
        MockMvc mvc = mockMvc();

        // Email que não existe — deve retornar 204 (sem enumerar usuários)
        mvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nonexistent_" + System.currentTimeMillis() + "@example.com\"}"))
                .andExpect(status().isNoContent());
    }
}
