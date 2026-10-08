package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.core.domain.exception.email.EmailVerificationCodeNotFoundException;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa {@link RegistrationController} com MockMvc standalone.
 * Cobre os caminhos de erro que os ITs não exercitam explicitamente.
 */
class RegistrationControllerTest {

    private MockMvc mockMvc;
    private UserUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = mock(UserUseCase.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(exceptionHandler, "lockoutDurationMinutes", 15L);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(useCase, publisher))
                .setControllerAdvice(exceptionHandler)
                .build();
    }

    // ── /auth/register ────────────────────────────────────────────────────────

    @Test
    void register_always_returns_403_registrationDisabled() throws Exception {
        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newuser\",\"password\":\"Secure@1\",\"email\":\"user@test.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REGISTRATION_DISABLED"));

        verifyNoInteractions(useCase);
    }

    @Test
    void register_withoutBody_returns_403() throws Exception {
        mockMvc.perform(post("/auth/register"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("REGISTRATION_DISABLED"));
    }

    // ── /auth/verify-email ────────────────────────────────────────────────────

    @Test
    void verifyEmail_valid_returns_204() throws Exception {
        when(useCase.verifyEmail("ABCDEF123456")).thenReturn("newuser");

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"ABCDEF123456\"}"))
                .andExpect(status().isNoContent());

        verify(useCase).verifyEmail("ABCDEF123456");
    }

    @Test
    void verifyEmail_invalidCode_returns_400() throws Exception {
        doThrow(new EmailVerificationCodeNotFoundException())
                .when(useCase).verifyEmail("INVALID12345");

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"INVALID12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VERIFICATION_CODE_INVALID"));
    }

    // ── /auth/resend-verification ─────────────────────────────────────────────

    @Test
    void resendVerification_always_returns_204() throws Exception {
        mockMvc.perform(post("/auth/resend-verification")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody@test.com\"}"))
                .andExpect(status().isNoContent());
    }
}
