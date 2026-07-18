package com.blog.blog_literario.exception;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.model.AuthorRequest;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.support.WebMvcTestConfig;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.DummyController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class,
        GlobalExceptionHandlerTest.DummyController.class})
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @RestController
    static class DummyController {
        record ValidBody(@NotBlank String name) {}

        @GetMapping("/test/not-found")
        void notFound() { throw new ResourceNotFoundException("Resource not found"); }

        @GetMapping("/test/conflict")
        void conflict() { throw new UserAlreadyExistsException("User already exists"); }

        @GetMapping("/test/bad-request")
        void badRequest() { throw new IllegalArgumentException("Invalid argument"); }

        @GetMapping("/test/illegal-state")
        void illegalState() { throw new IllegalStateException("Illegal state"); }

        @GetMapping("/test/optimistic-lock")
        void optimisticLock() { throw new ObjectOptimisticLockingFailureException(AuthorRequest.class, 1); }

        @PostMapping("/test/validate")
        void validate(@Validated @RequestBody ValidBody body) {}
    }

    @Autowired MockMvc mockMvc;

    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void resourceNotFound_returns404WithRFC7807() throws Exception {
        mockMvc.perform(get("/test/not-found").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Resource not found"));
    }

    @Test
    void userAlreadyExists_returns409WithRFC7807() throws Exception {
        mockMvc.perform(get("/test/conflict").with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void illegalArgument_returns400WithRFC7807() throws Exception {
        mockMvc.perform(get("/test/bad-request").with(user("admin").roles("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/bad-request"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void illegalState_returns409WithRFC7807() throws Exception {
        mockMvc.perform(get("/test/illegal-state").with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void optimisticLockConflict_returns409WithFixedSpanishDetail() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock").with(user("admin").roles("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"))
                .andExpect(jsonPath("$.status").value(409))
                // Fixed user-facing detail — the raw message names entity classes
                .andExpect(jsonPath("$.detail").value(
                        "La solicitud fue modificada por otra persona al mismo tiempo. Recarga la página e inténtalo de nuevo."));
    }

    @Test
    void validationFailure_returns400WithValidationType() throws Exception {
        mockMvc.perform(post("/test/validate")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isString());
    }
}
