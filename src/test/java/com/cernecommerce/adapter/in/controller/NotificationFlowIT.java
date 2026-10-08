package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.ports.in.UserUseCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo end-to-end de notificações:
 * trocar senha → notificação PASSWORD_CHANGED criada → marcar como lida → deletar
 */
@SpringBootTest
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationFlowIT {

    @Autowired private WebApplicationContext context;
    @Autowired private EmailVerificationTestHelper verificationHelper;
    @Autowired private UserUseCase userUseCase;
    @Autowired private LoginRateLimiterPort rateLimiter;
    @Autowired private LoginAttemptPort loginAttempt;

    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void reset() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter r) r.reset();
        if (loginAttempt instanceof InMemoryLoginAttemptAdapter a) a.clearAll();
    }

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void notificacao_criada_ao_trocar_senha_e_gerenciada_corretamente() throws Exception {
        MockMvc mvc = mvc();
        String suffix = String.valueOf(System.currentTimeMillis());
        String username = "notif_" + suffix;
        String password = "Secure@123";
        String newPassword = "NewSecure@456";

        // Register + verify + login
        register(mvc, username, password, username + "@test.com");
        verifyEmail(mvc, username, verificationHelper.getCodeForUsername(username));
        String token = login(mvc, username, password);

        // Change password → fires USER_PASSWORD_CHANGED event → async notification
        mvc.perform(put("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + password + "\","
                                + "\"newPassword\":\"" + newPassword + "\","
                                + "\"revokeOtherSessions\":false}"))
                .andExpect(status().isNoContent());

        // Re-login with new password (old token may be invalidated by password change)
        String freshToken = login(mvc, username, newPassword);

        // Wait for async listener to persist the notification
        long notifId = awaitNotification(mvc, freshToken);

        // unread-count must be >= 1
        MvcResult countResult = mvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andReturn();
        int count = om.readTree(countResult.getResponse().getContentAsString()).get("count").asInt();
        assertThat(count).isGreaterThanOrEqualTo(1);

        // Mark as read → 204
        mvc.perform(patch("/notifications/" + notifId + "/read")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isNoContent());

        // After mark-as-read, unread-count must drop
        MvcResult countAfterRead = mvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andReturn();
        int countAfter = om.readTree(countAfterRead.getResponse().getContentAsString()).get("count").asInt();
        assertThat(countAfter).isLessThan(count);

        // Delete → 204
        mvc.perform(delete("/notifications/" + notifId)
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isNoContent());

        // Notification must be gone from the list
        MvcResult listResult = mvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode content = om.readTree(listResult.getResponse().getContentAsString()).get("content");
        boolean found = false;
        for (JsonNode n : content) {
            if (n.get("id").asLong() == notifId) { found = true; break; }
        }
        assertThat(found).isFalse();
    }

    @Test
    void marcar_todas_como_lidas_zera_unread_count() throws Exception {
        MockMvc mvc = mvc();
        String suffix = String.valueOf(System.currentTimeMillis() + 1);
        String username = "notif2_" + suffix;
        String password = "Secure@123";
        String newPassword = "NewSecure@789";

        register(mvc, username, password, username + "@test.com");
        verifyEmail(mvc, username, verificationHelper.getCodeForUsername(username));
        String token = login(mvc, username, password);

        mvc.perform(put("/users/me/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + password + "\","
                                + "\"newPassword\":\"" + newPassword + "\","
                                + "\"revokeOtherSessions\":false}"))
                .andExpect(status().isNoContent());

        String freshToken = login(mvc, username, newPassword);
        awaitNotification(mvc, freshToken);

        // Mark all as read → 204
        mvc.perform(patch("/notifications/read-all")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isNoContent());

        // unread-count must be 0
        MvcResult countResult = mvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andReturn();
        int count = om.readTree(countResult.getResponse().getContentAsString()).get("count").asInt();
        assertThat(count).isZero();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void register(MockMvc mvc, String username, String password, String email) {
        userUseCase.registerUser(username, password, email, List.of());
    }

    private void verifyEmail(MockMvc mvc, String username, String code) throws Exception {
        mvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isNoContent());
    }

    private String login(MockMvc mvc, String username, String password) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** Polls GET /notifications until at least one entry is present (async listener latency). */
    private long awaitNotification(MockMvc mvc, String token) throws Exception {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            MvcResult r = mvc.perform(get("/notifications")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode content = om.readTree(r.getResponse().getContentAsString()).get("content");
            if (content.size() > 0) {
                return content.get(0).get("id").asLong();
            }
            Thread.sleep(50);
        }
        throw new AssertionError("No notification appeared within 3 seconds");
    }
}
