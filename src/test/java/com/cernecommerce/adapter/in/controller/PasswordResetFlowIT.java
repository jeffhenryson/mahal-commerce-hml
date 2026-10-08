package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.adapter.out.email.LoggingEmailAdapter;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.infra.security.support.EmailVerificationTestHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa o fluxo completo de recuperação de senha:
 * POST /auth/forgot-password → POST /auth/reset-password → POST /auth/login (com nova senha)
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class PasswordResetFlowIT {

    @Autowired
    private UserUseCase userUseCase;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EmailVerificationTestHelper verificationHelper;

    @Autowired
    private LoggingEmailAdapter loggingEmailAdapter;

    @Autowired
    private LoginRateLimiterPort rateLimiter;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void resetRateLimiter() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Registra e verifica email de um usuário, retorna a senha original. */
    private String registerAndVerify(MockMvc mvc, String username, String email, String password) throws Exception {
        userUseCase.registerUser(username, password, email, List.of());

        String code = verificationHelper.getCodeForUsername(username);
        mvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        return password;
    }

    /** Extrai o token do link de reset (ex: http://...?token=TOKEN). */
    private String extractToken(String resetLink) {
        int idx = resetLink.indexOf("?token=");
        if (idx == -1) throw new IllegalStateException("Token not found in reset link: " + resetLink);
        return resetLink.substring(idx + 7);
    }

    @Test
    void forgot_password_then_reset_then_login_with_new_password() throws Exception {
        MockMvc mvc = mockMvc();
        String ts = String.valueOf(System.currentTimeMillis());
        String username = "resetuser_" + ts;
        String email = username + "@test.com";
        String oldPassword = "OldPass@1";
        String newPassword = "NewPass@2";

        registerAndVerify(mvc, username, email, oldPassword);

        // 1. Solicitar reset — deve retornar 204 independente de o email existir
        mvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        // 2. Recuperar token do "email" enviado pelo LoggingEmailAdapter
        String resetLink = loggingEmailAdapter.getLastResetLinkForUsername(username);
        assert resetLink != null : "Reset link deve ser gerado para email existente";
        String token = extractToken(resetLink);

        // 3. Redefinir senha com token válido — deve retornar 204
        mvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent());

        // 4. Login com senha antiga deve falhar (sessões revogadas + senha alterada)
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + oldPassword + "\"}"))
                .andExpect(status().isUnauthorized());

        // 5. Login com nova senha deve funcionar
        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void forgot_password_reset_blocks_previously_issued_access_token() throws Exception {
        MockMvc mvc = mockMvc();
        String ts = String.valueOf(System.currentTimeMillis());
        String username = "resetblock_" + ts;
        String email = username + "@test.com";
        String oldPassword = "OldPass@1";
        String newPassword = "NewPass@2";

        registerAndVerify(mvc, username, email, oldPassword);

        // Login to get an access token issued BEFORE the reset
        MvcResult loginResult = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + oldPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = om.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();

        // 1. Solicitar reset — deve retornar 204 independente de o email existir
        mvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        // 2. Recuperar token do "email" enviado pelo LoggingEmailAdapter
        String resetLink = loggingEmailAdapter.getLastResetLinkForUsername(username);
        assert resetLink != null : "Reset link deve ser gerado para email existente";
        String token = extractToken(resetLink);

        // 3. Redefinir senha com token válido — deve retornar 204
        mvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent());

        // 4. Access token emitido antes do reset deve ser bloqueado (tokenBlocklistPort.blockAllBefore)
        mvc.perform(get("/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void forgot_password_with_nonexistent_email_returns_204_silently() throws Exception {
        MockMvc mvc = mockMvc();

        // Sempre retorna 204 para evitar enumeração de usuários
        mvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"ghost_" + System.currentTimeMillis() + "@example.com\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void reset_password_with_invalid_token_returns_400() throws Exception {
        MockMvc mvc = mockMvc();

        mvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"invalidtoken123\",\"newPassword\":\"NewPass@1\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reset_password_cannot_be_used_twice() throws Exception {
        MockMvc mvc = mockMvc();
        String ts = String.valueOf(System.currentTimeMillis());
        String username = "twicereset_" + ts;
        String email = username + "@test.com";

        registerAndVerify(mvc, username, email, "OldPass@1");

        mvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        String token = extractToken(loggingEmailAdapter.getLastResetLinkForUsername(username));

        // Primeiro uso — deve funcionar
        mvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewPass@2\"}"))
                .andExpect(status().isNoContent());

        // Segundo uso do mesmo token — deve retornar 400 (token já usado)
        mvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"AnotherPass@3\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reset_password_with_weak_password_returns_400() throws Exception {
        MockMvc mvc = mockMvc();
        String ts = String.valueOf(System.currentTimeMillis());
        String username = "weakpwd_" + ts;
        String email = username + "@test.com";

        registerAndVerify(mvc, username, email, "OldPass@1");

        mvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());

        String token = extractToken(loggingEmailAdapter.getLastResetLinkForUsername(username));

        mvc.perform(post("/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest());
    }
}
