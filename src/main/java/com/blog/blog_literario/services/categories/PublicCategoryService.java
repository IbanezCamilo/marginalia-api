package com.blog.blog_literario.services.categories;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.repositories.CategoryRepository;

import lombok.RequiredArgsConstructor;

/**
 * Read-only service for the public category feed. Exposes categories without
 * any authentication requirement.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponse> listCategories() {
        return categoryRepository
                .findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * @throws RuntimeException if no category exists with the given {@code slug}
     */
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository
                .findBySlug(slug)
                .orElseThrow(() -> new RuntimeException("Category no encontrada: " + slug));

        return toResponse(category);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }
}
