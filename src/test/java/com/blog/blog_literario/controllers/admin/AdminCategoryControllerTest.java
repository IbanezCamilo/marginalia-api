package com.blog.blog_literario.controllers.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.dto.categories.CreateCategoryRequest;
import com.blog.blog_literario.dto.categories.UpdateCategoryRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.categories.CategoryService;
import com.blog.blog_literario.support.WebMvcTestConfig;

@WebMvcTest(AdminCategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class AdminCategoryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean CategoryService categoryService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    @Test
    void createCategory_asAdmin_validRequest_returns201() throws Exception {
        given(categoryService.createCategory(any(CreateCategoryRequest.class)))
                .willReturn(new CategoryResponse(1, "Fiction", "fiction"));

        mockMvc.perform(post("/api/admin/categories")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Fiction\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("fiction"));
    }

    @Test
    void createCategory_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"));
    }

    @Test
    void createCategory_nameTooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/validation"));
    }

    @Test
    void createCategory_duplicateName_returns409WithProblemDetail() throws Exception {
        given(categoryService.createCategory(any(CreateCategoryRequest.class)))
                .willThrow(new IllegalStateException("La categoria 'Fiction' ya existe"));

        mockMvc.perform(post("/api/admin/categories")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Fiction\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"));
    }

    @Test
    void createCategory_asReader_returns403() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                .with(user("reader").roles("READER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Fiction\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateCategory_asAdmin_returns200() throws Exception {
        given(categoryService.updateCategory(eq(1), any(UpdateCategoryRequest.class)))
                .willReturn(new CategoryResponse(1, "Drama", "drama"));

        mockMvc.perform(put("/api/admin/categories/1")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Drama\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("drama"));
    }

    @Test
    void updateCategory_nonExistent_returns404WithProblemDetail() throws Exception {
        given(categoryService.updateCategory(eq(99), any(UpdateCategoryRequest.class)))
                .willThrow(new ResourceNotFoundException("Categoria no encontrada con ID: 99"));

        mockMvc.perform(put("/api/admin/categories/99")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Drama\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void updateCategory_slugCollision_returns409WithProblemDetail() throws Exception {
        given(categoryService.updateCategory(eq(1), any(UpdateCategoryRequest.class)))
                .willThrow(new IllegalStateException("Ya existe una categoria con el slug 'drama'"));

        mockMvc.perform(put("/api/admin/categories/1")
                .with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Drama\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/conflict"));
    }

    @Test
    void deleteCategory_asAdmin_returns204() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/1").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteCategory_nonExistent_returns404WithProblemDetail() throws Exception {
        org.mockito.BDDMockito.willThrow(new ResourceNotFoundException("Categoria no encontrada con ID: 99"))
                .given(categoryService).deleteCategory(99);

        mockMvc.perform(delete("/api/admin/categories/99").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }

    @Test
    void deleteCategory_asAuthor_returns403() throws Exception {
        mockMvc.perform(delete("/api/admin/categories/1").with(user("author").roles("AUTHOR")))
                .andExpect(status().isForbidden());
    }
}
