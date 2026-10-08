package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginAttemptAdapter;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.core.ports.out.ratelimit.LoginAttemptPort;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.infra.security.support.EmailVerificationTestHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica que eventos críticos de auditoria são efetivamente persistidos no banco.
 * Usa H2 em memória com perfil dev — sem dependências externas.
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class AuditEventsIT {

    @Autowired private WebApplicationContext context;
    @Autowired private AuditLogJpaRepository auditRepo;
    @Autowired private LoginRateLimiterPort rateLimiter;
    @Autowired private LoginAttemptPort loginAttempt;
    @Autowired private EmailVerificationTestHelper verificationHelper;
    @Autowired private UserUseCase userUseCase;

    @BeforeEach
    void reset() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter r) r.reset();
        if (loginAttempt instanceof InMemoryLoginAttemptAdapter a) a.clearAll();
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Aguarda até 2 segundos que uma entrada com a action apareça no banco (async). */
    private void awaitAction(String action) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (auditRepo.findAll().stream().anyMatch(e -> action.equals(e.getAction()))) return;
            Thread.sleep(50);
        }
        assertThat(auditRepo.findAll().stream().anyMatch(e -> action.equals(e.getAction())))
                .as("Expected audit entry with action=" + action).isTrue();
    }

    @Test
    void login_with_wrong_password_persists_LOGIN_FAILED() throws Exception {
        mvc().perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrongPassword!\"}"))
                .andExpect(status().isUnauthorized());

        awaitAction("LOGIN_FAILED");
    }

    @Test
    void account_locked_after_max_attempts_persists_ACCOUNT_LOCKED() throws Exception {
        // 5 tentativas com senha errada para acionar o lock (max-attempts=5 no dev)
        for (int i = 0; i < 5; i++) {
            mvc().perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin\",\"password\":\"wrong" + i + "!\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // 6ª tentativa — conta bloqueada
        mvc().perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong6!\"}"))
                .andExpect(status().isTooManyRequests());

        awaitAction("ACCOUNT_LOCKED");
    }

    @Test
    void email_verification_persists_USER_EMAIL_VERIFIED() throws Exception {
        String username = "verifyaudit_" + System.currentTimeMillis();

        userUseCase.registerUser(username, "Secure@1", username + "@test.com", List.of());

        String code = verificationHelper.getCodeForUsername(username);

        mvc().perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        awaitAction("USER_EMAIL_VERIFIED");
    }
}
