package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.adapter.out.email.LoggingEmailAdapter;
import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa que requisições concorrentes de reset de senha com o mesmo token consomem o token
 * exatamente uma vez — garantindo a segurança do CAS atômico
 * ({@code PasswordResetTokenJpaRepository.markAsUsedIfNotClaimed}). Sem essa atomicidade,
 * duas requisições poderiam ambas trocar a senha com o mesmo token, deixando o estado do
 * usuário inconsistente entre as duas revogações de sessão disparadas (C008).
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetConcurrencyIT {

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

    @BeforeEach
    void resetRateLimiter() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String extractToken(String resetLink) {
        int idx = resetLink.indexOf("?token=");
        if (idx == -1) throw new IllegalStateException("Token not found in reset link: " + resetLink);
        return resetLink.substring(idx + 7);
    }

    @Test
    void concurrent_reset_with_same_token_succeeds_exactly_once() throws Exception {
        MockMvc mvc = mockMvc();
        String username = "pwreset_" + System.currentTimeMillis();
        String email = username + "@test.com";
        String oldPassword = "OldPass@1";

        userUseCase.registerUser(username, oldPassword, email, List.of());
        String code = verificationHelper.getCodeForUsername(username);
        mvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isNoContent());
        String token = extractToken(loggingEmailAdapter.getLastResetLinkForUsername(username));

        int threads = 5;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            int idx = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    MvcResult result = mvc.perform(post("/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"token\":\"" + token + "\",\"newPassword\":\"NewPass@" + idx + "x\"}"))
                            .andReturn();
                    if (result.getResponse().getStatus() == 204) successes.incrementAndGet();
                    else failures.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        ready.await();
        start.countDown();
        for (Future<?> f : futures) f.get();
        executor.shutdown();

        assertThat(successes.get())
                .as("Exatamente um reset de senha deve ter sucesso com o mesmo token")
                .isEqualTo(1);
        assertThat(failures.get())
                .as("As demais requisições devem falhar — token já usado")
                .isEqualTo(threads - 1);
    }
}
