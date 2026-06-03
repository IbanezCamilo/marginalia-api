package com.blog.blog_literario.controllers.categories;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.services.categories.PublicCategoryService;

import lombok.RequiredArgsConstructor;

/**
 * Public read-only endpoints for category browsing. No authentication required.
 */
@RestController
@RequestMapping("api/public/categories")
@RequiredArgsConstructor
public class PublicCategoryController {

    private final PublicCategoryService publicCategoryService;

    @GetMapping
    public List<CategoryResponse> list() {
        return publicCategoryService.listCategories();
    }

    @GetMapping("/{slug}")
    public CategoryResponse detail(@PathVariable String slug) {
        return publicCategoryService.getBySlug(slug);
    }
}
