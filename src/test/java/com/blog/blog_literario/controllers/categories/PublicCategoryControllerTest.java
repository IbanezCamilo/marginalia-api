package com.blog.blog_literario.controllers.categories;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.blog.blog_literario.config.SecurityConfig;
import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.security.JwtAuthenticationFilter;
import com.blog.blog_literario.security.JwtService;
import com.blog.blog_literario.security.RateLimitFilter;
import com.blog.blog_literario.security.UserDetailsServiceImpl;
import com.blog.blog_literario.services.categories.PublicCategoryService;
import com.blog.blog_literario.support.WebMvcTestConfig;

@WebMvcTest(PublicCategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RateLimitFilter.class, WebMvcTestConfig.class})
@ActiveProfiles("test")
class PublicCategoryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean PublicCategoryService publicCategoryService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private static final CategoryResponse SAMPLE = new CategoryResponse(1, "Fiction", "fiction");

    @Test
    void list_returns200WithCategoryArray() throws Exception {
        given(publicCategoryService.listCategories()).willReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].slug").value("fiction"));
    }

    @Test
    void list_emptyResult_returns200WithEmptyArray() throws Exception {
        given(publicCategoryService.listCategories()).willReturn(List.of());

        mockMvc.perform(get("/api/public/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void detail_existingSlug_returns200() throws Exception {
        given(publicCategoryService.getBySlug("fiction")).willReturn(SAMPLE);

        mockMvc.perform(get("/api/public/categories/fiction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Fiction"))
                .andExpect(jsonPath("$.slug").value("fiction"));
    }

    @Test
    void detail_nonExistentSlug_returns404WithProblemDetail() throws Exception {
        given(publicCategoryService.getBySlug("missing"))
                .willThrow(new ResourceNotFoundException("Categoria no encontrada con slug: missing"));

        mockMvc.perform(get("/api/public/categories/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://blog-literario.com/errors/not-found"));
    }
}
