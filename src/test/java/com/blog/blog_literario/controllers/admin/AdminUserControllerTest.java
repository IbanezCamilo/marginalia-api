package com.blog.blog_literario.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.admin.AdminResetPasswordRequest;
import com.blog.blog_literario.dto.roles.RoleResponse;
import com.blog.blog_literario.dto.users.UpdateUserRequest;
import com.blog.blog_literario.dto.users.UserResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.security.CorrelationIdFilter;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.admin.AdminUserService;
import com.blog.blog_literario.support.TestSecurityFactory;
import com.blog.blog_literario.support.WebMvcTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class AdminUserControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AdminUserService adminUserService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final UserResponse SAMPLE_USER = new UserResponse(
            1, "Alice", "alice@test.com",
            new RoleResponse(2, "READER"), LocalDateTime.now());

    @Test
    void getAllUsers_asAdmin_returns200() throws Exception {
        given(adminUserService.getAllUsers(any(Pageable.class))).willReturn(new PageImpl<>(List.of(SAMPLE_USER)));

        mockMvc.perform(get("/api/admin/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAllUsers_asOwner_returns200() throws Exception {
        given(adminUserService.getAllUsers(any(Pageable.class))).willReturn(new PageImpl<>(List.of(SAMPLE_USER)));

        mockMvc.perform(get("/api/admin/users").with(user("owner").roles("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void getAllUsers_asReader_returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users").with(user("reader").roles("READER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void searchUsers_asAdmin_delegatesQuery() throws Exception {
        given(adminUserService.searchUsers(eq("alice"), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(SAMPLE_USER)));

        mockMvc.perform(get("/api/admin/users/search?q=alice").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(adminUserService).searchUsers(eq("alice"), any(Pageable.class));
    }

    @Test
    void getUserById_nonExistent_returns404WithProblemDetail() throws Exception {
        given(adminUserService.getUserById(99))
                .willThrow(new ResourceNotFoundException("User not found with ID: 99"));

        mockMvc.perform(get("/api/admin/users/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createUser_asAdmin_validRequest_returns201() throws Exception {
        given(adminUserService.createUser(eq(2), any())).willReturn(SAMPLE_USER);

        mockMvc.perform(post("/api/admin/users")
                .with(authentication(TestSecurityFactory.asAdmin(2)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bob\",\"email\":\"bob@test.com\",\"password\":\"password123\",\"roleName\":\"READER\"}"))
                .andExpect(status().isCreated());

        verify(adminUserService).createUser(eq(2), any());
    }

    @Test
    void deleteUser_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/users/1").with(authentication(TestSecurityFactory.asAdmin(2))))
                .andExpect(status().isNoContent());

        verify(adminUserService).deleteUser(eq(2), eq(1));
    }

    @Test
    void updateUser_asAdmin_validRequest_returns200() throws Exception {
        given(adminUserService.update(eq(2), eq(1), any(UpdateUserRequest.class))).willReturn(SAMPLE_USER);

        mockMvc.perform(put("/api/admin/users/1")
                .with(authentication(TestSecurityFactory.asAdmin(2)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bob\",\"email\":null,\"roleName\":null}"))
                .andExpect(status().isOk());

        verify(adminUserService).update(eq(2), eq(1), any(UpdateUserRequest.class));
    }

    @Test
    void resetPassword_asAdmin_validRequest_returns200() throws Exception {
        given(adminUserService.resetPassword(eq(2), eq(1), any(AdminResetPasswordRequest.class)))
                .willReturn(SAMPLE_USER);

        mockMvc.perform(put("/api/admin/users/1/password")
                .with(authentication(TestSecurityFactory.asAdmin(2)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"newPassword123\"}"))
                .andExpect(status().isOk());

        verify(adminUserService).resetPassword(eq(2), eq(1), any(AdminResetPasswordRequest.class));
    }
}
