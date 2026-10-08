package com.cernecommerce.adapter.in.controller;

import com.cernecommerce.adapter.out.security.ratelimit.InMemoryLoginRateLimiterAdapter;
import com.cernecommerce.core.domain.exception.email.EmailDeliveryException;
import com.cernecommerce.core.domain.model.PageResult;
import com.cernecommerce.core.domain.model.auth.User;
import com.cernecommerce.adapter.out.email.LoggingEmailAdapter;
import com.cernecommerce.core.ports.out.ratelimit.LoginRateLimiterPort;
import com.cernecommerce.core.ports.in.UserUseCase;
import com.cernecommerce.core.ports.out.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

/**
 * PLAT-C053 e PLAT-C054 contra o banco de verdade (H2 em modo PostgreSQL), porque o defeito de
 * C053 mora na fronteira da transação — um teste com mocks de repositório não o enxerga.
 */
@SpringBootTest
@ActiveProfiles("dev")
// O spy de EmailPort obriga um contexto próprio; com o H2 compartilhado (jdbc:h2:mem:demo), o
// create-drop ao fechá-lo derrubaria o schema dos outros contextos em cache (PLAT-C052).
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:user-registration-reliability;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserRegistrationReliabilityIT {

    @Autowired UserRepository userRepository;
    @Autowired UserUseCase userUseCase;
    @Autowired LoginRateLimiterPort rateLimiter;
    // Spy (não mock) porque EmailVerificationTestHelper injeta o bean pelo tipo concreto.
    @MockitoSpyBean LoggingEmailAdapter emailPort;

    @BeforeEach
    void resetRateLimiter() {
        if (rateLimiter instanceof InMemoryLoginRateLimiterAdapter rl) rl.reset();
    }

    @Test
    void emailFailure_doesNotPersistAccount_andRetrySucceeds() throws Exception {
        String username = "c053_" + System.nanoTime();
        String email = username + "@test.com";

        doThrow(new EmailDeliveryException("provider down"))
                .when(emailPort).sendVerificationCode(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> userUseCase.registerUser(username, "Secure@123", email, List.of()))
                .isInstanceOf(EmailDeliveryException.class);

        // Antes da correção a conta ficava gravada (desabilitada) e a próxima tentativa dava 409.
        assertThat(userRepository.findByUsername(username)).isEmpty();
        assertThat(userRepository.findByEmail(email)).isEmpty();

        doCallRealMethod().when(emailPort).sendVerificationCode(anyString(), anyString(), anyString());

        userUseCase.registerUser(username, "Secure@123", email, List.of());
        assertThat(userRepository.findByUsername(username)).isPresent();
    }

    @Test
    void userManagementListing_excludesShopCustomers() {
        String tag = "c054_" + System.nanoTime();
        userRepository.save(User.of(tag + "_op", "hashed", new HashSet<>()));
        userRepository.save(User.customer(tag + "_cli@test.com", "hashed", tag + "_cli@test.com",
                System.nanoTime(), new HashSet<>()));

        PageResult<User> page = userRepository.findFiltered(tag, null, "id", "asc", 0, 20, Set.of());

        assertThat(page.content()).extracting(User::getUsername).containsExactly(tag + "_op");
        assertThat(page.totalElements()).isEqualTo(1);
    }
}
