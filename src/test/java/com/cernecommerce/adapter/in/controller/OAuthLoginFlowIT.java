package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.auth.OAuthTokenInvalidException;
import com.cernecommerce.core.domain.model.auth.GoogleUserInfo;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.oauth.GoogleTokenVerifierPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("dev")
class OAuthLoginFlowIT {

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private GoogleTokenVerifierPort tokenVerifier;

    @Autowired
    private UserUseCase userUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** O login Google não cria contas: o usuário precisa existir (criado por dev/admin). */
    private void existingUser(String username, String email) {
        userUseCase.createUser(username, "Senha@123", email, List.of());
    }

    // ── happy paths ───────────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_existingEmail_linksAndReturns200_withAccessToken() throws Exception {
        existingUser("google_it_001", "newuser@example.com");
        when(tokenVerifier.verify("valid-token"))
                .thenReturn(new GoogleUserInfo("google-it-001", "newuser@example.com", "New User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"valid-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void loginWithGoogle_normalizes_email_to_lowercase() throws Exception {
        // Email com uppercase — deve encontrar o usuário com email em lowercase
        existingUser("google_it_002", "uppercase@example.com");
        when(tokenVerifier.verify("uppercase-token"))
                .thenReturn(new GoogleUserInfo("google-it-002", "UpperCase@Example.COM", "Upper User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"uppercase-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // Segunda chamada com mesmo email em case diferente deve retornar o mesmo usuário (via googleId link)
        when(tokenVerifier.verify("uppercase-token-2"))
                .thenReturn(new GoogleUserInfo("google-it-002", "uppercase@example.com", "Upper User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"uppercase-token-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void loginWithGoogle_secondLogin_sameUser_succeeds() throws Exception {
        existingUser("google_it_003", "repeat@example.com");
        when(tokenVerifier.verify("repeat-token"))
                .thenReturn(new GoogleUserInfo("google-it-003", "repeat@example.com", "Repeat User"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"repeat-token\"}"))
                .andExpect(status().isOk());

        // Segundo login com o mesmo Google ID deve funcionar
        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"repeat-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    // ── error paths ───────────────────────────────────────────────────────────

    @Test
    void loginWithGoogle_unknownEmail_returns403_userNotFound() throws Exception {
        when(tokenVerifier.verify("unknown-token"))
                .thenReturn(new GoogleUserInfo("google-it-404", "ninguem@example.com", "Ninguém"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"unknown-token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND"));
    }

    @Test
    void loginWithGoogle_invalidToken_returns401() throws Exception {
        when(tokenVerifier.verify("bad-token"))
                .thenThrow(new OAuthTokenInvalidException("Token Google inválido ou expirado"));

        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"bad-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("OAUTH_TOKEN_INVALID"));
    }

    @Test
    void loginWithGoogle_missingIdToken_returns400() throws Exception {
        mockMvc.perform(post("/auth/oauth2/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
