package com.cernecommerce.adapter.in.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cernecommerce.core.domain.exception.user.InviteEmailRequiredException;
import com.cernecommerce.adapter.in.converter.UserDTOConverter;
import com.cernecommerce.adapter.in.dtos.request.CreateUserRequest;
import com.cernecommerce.core.domain.event.AuditEvent;
import com.cernecommerce.core.domain.exception.rbac.RoleNotFoundException;
import com.cernecommerce.core.domain.exception.user.UserNotFoundException;
import com.cernecommerce.core.domain.exception.user.UsernameAlreadyExistsException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.UpdateProfileResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.infra.handler.GlobalExceptionHandler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;

public class UserControllerTest {

    private MockMvc mockMvc;
    private UserUseCase useCase;
    private ApplicationEventPublisher publisher;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        useCase = mock(UserUseCase.class);
        publisher = mock(ApplicationEventPublisher.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(useCase, new UserDTOConverter("http://localhost:8080"), publisher))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private User user(Long id, String username) {
        return User.fromPersisted(id, username, "hashed", true, null, false, null, null, null, new HashSet<>(), null, null);
    }

    @Test
    void get_user_by_id_returns_200_with_user_data() throws Exception {
        when(useCase.getUserById(1L)).thenReturn(user(1L, "alice"));

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void get_user_by_id_not_found_returns_404() throws Exception {
        when(useCase.getUserById(999L)).thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void delete_user_returns_204_and_calls_use_case() throws Exception {
        when(useCase.deleteUser(42L)).thenReturn("alice");

        mockMvc.perform(delete("/users/42"))
                .andExpect(status().isNoContent());

        verify(useCase).deleteUser(42L);
    }

    @Test
    void delete_user_not_found_returns_404() throws Exception {
        doThrow(new UserNotFoundException(999L)).when(useCase).deleteUser(999L);

        mockMvc.perform(delete("/users/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_user_duplicate_username_returns_409() throws Exception {
        when(useCase.createUser(eq("admin"), any(), any(), any()))
                .thenThrow(new UsernameAlreadyExistsException("admin"));

        CreateUserRequest dto = new CreateUserRequest();
        dto.setUsername("admin");
        dto.setPassword("Secret@123");
        dto.setEmail("admin@test.com");

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(dto)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_user_without_password_invites_and_flags_event() throws Exception {
        when(useCase.inviteUser(eq("novo"), eq("novo@test.com"), any())).thenReturn(user(7L, "novo"));

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"novo\",\"email\":\"novo@test.com\"}"))
                .andExpect(status().isCreated());

        verify(useCase, never()).createUser(any(), any(), any(), any());
        verify(publisher).publishEvent(argThat((Object e) -> e instanceof AuditEvent ev
                && ev.type() == AuditEvent.EventType.USER_CREATED && Boolean.TRUE.equals(ev.details().get("invited"))));
    }

    @Test
    void create_user_with_password_keeps_direct_creation() throws Exception {
        when(useCase.createUser(eq("john"), eq("Secret@123"), eq("john@test.com"), any())).thenReturn(user(8L, "john"));

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"john\",\"password\":\"Secret@123\",\"email\":\"john@test.com\"}"))
                .andExpect(status().isCreated());

        verify(useCase, never()).inviteUser(any(), any(), any());
    }

    @Test
    void create_user_invite_without_email_returns_400() throws Exception {
        when(useCase.inviteUser(eq("semmail"), isNull(), any())).thenThrow(new InviteEmailRequiredException());

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"semmail\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVITE_EMAIL_REQUIRED"));
    }

    @Test
    void resend_invite_returns_204() throws Exception {
        mockMvc.perform(post("/users/7/invite/resend"))
                .andExpect(status().isNoContent());

        verify(useCase).resendInvite(7L);
    }

    @Test
    void create_user_blank_username_returns_400() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"\",\"password\":\"Secret@123\",\"email\":\"a@b.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void create_user_missing_body_returns_400() throws Exception {
        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void assign_role_unknown_role_returns_404() throws Exception {
        doThrow(new RoleNotFoundException("ROLE_GHOST"))
                .when(useCase).assignRole("alice", "ROLE_GHOST");

        mockMvc.perform(post("/users/alice/roles/ROLE_GHOST"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void assign_role_unknown_user_returns_404() throws Exception {
        doThrow(new UserNotFoundException("ghost"))
                .when(useCase).assignRole("ghost", "ROLE_USER");

        mockMvc.perform(post("/users/ghost/roles/ROLE_USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void list_users_returns_paged_result() throws Exception {
        when(useCase.findFiltered(isNull(), isNull(), eq("id"), eq("asc"), eq(0), eq(20), any())).thenReturn(
                new PageResult<>(List.of(user(1L, "alice"), user(2L, "bob")), 0, 20, 2L, 1));

        mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].username").value("alice"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void update_user_without_email_change_publishes_USER_UPDATED() throws Exception {
        User alice = user(1L, "alice");
        when(useCase.updateUser(1L, "alice", null))
                .thenReturn(new UpdateProfileResult(alice, false));

        mockMvc.perform(patch("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent((Object) argThat(e ->
                e instanceof AuditEvent ae &&
                ae.type() == AuditEvent.EventType.USER_UPDATED));
    }

    @Test
    void update_user_with_email_change_publishes_EMAIL_CHANGE_REQUESTED() throws Exception {
        User alice = user(1L, "alice");
        when(useCase.updateUser(1L, "alice", "new@email.com"))
                .thenReturn(new UpdateProfileResult(alice, true));

        mockMvc.perform(patch("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"new@email.com\"}"))
                .andExpect(status().isOk());

        verify(publisher).publishEvent((Object) argThat(e ->
                e instanceof AuditEvent ae &&
                ae.type() == AuditEvent.EventType.EMAIL_CHANGE_REQUESTED));
    }

    @Test
    void update_user_empty_email_returns_400() throws Exception {
        mockMvc.perform(patch("/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
