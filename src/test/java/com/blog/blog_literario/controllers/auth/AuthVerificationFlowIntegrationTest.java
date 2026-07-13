package com.blog.blog_literario.controllers.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

import com.blog.blog_literario.events.VerificationEmailRequested;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.security.CookieUtil;

/**
 * Full-stack regression test for the registration → verification → login flow,
 * using the real security filter chain, the real {@code AuthenticationManager}
 * (DaoAuthenticationProvider + BCrypt), real transactions, and the real MVC
 * exception mapping — none of which the mocked unit tests exercise.
 *
 * <p>Guards specifically against the unverified-login case answering
 * 401 "Credenciales inválidas" instead of the intended 403 email-not-verified
 * ProblemDetail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@RecordApplicationEvents
class AuthVerificationFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired ApplicationEvents events;

    private static final String EMAIL = "flow-user@test.com";
    private static final String PASSWORD = "password123";

    private String registerBody() {
        return "{\"name\":\"Flujo\",\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private String loginBody(String password) {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void registerThenLoginUnverified_returns403EmailNotVerified_andVerifiedLoginSucceeds() throws Exception {
        // 1. Register: 201, no session cookies.
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody()))
                .andExpect(status().isCreated())
                .andExpect(cookie().doesNotExist(CookieUtil.NAME))
                .andExpect(cookie().doesNotExist(CookieUtil.REFRESH_COOKIE_NAME));

        // 2. Login with the CORRECT password while unverified: must be the
        //    403 verification ProblemDetail, never 401 "Credenciales inválidas".
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/email-not-verified"))
                .andExpect(cookie().doesNotExist(CookieUtil.NAME));

        // 3. The blocked-but-correct login must not count toward lockout.
        User stored = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(stored.getFailedLoginAttempts()).isZero();
        assertThat(stored.isEmailVerified()).isFalse();

        // 4. Verify using the raw token carried by the registration event.
        String rawToken = events.stream(VerificationEmailRequested.class)
                .filter(e -> EMAIL.equals(e.email()))
                .findFirst().orElseThrow()
                .rawToken();

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        // 5. Same credentials now log in fully, with both cookies.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(CookieUtil.NAME))
                .andExpect(cookie().exists(CookieUtil.REFRESH_COOKIE_NAME));

        // 6. Control: a wrong password is a plain 401, independent of verification.
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Credenciales inválidas"));
    }
}
