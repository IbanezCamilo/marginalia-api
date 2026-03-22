package com.blog.blog_literario.services.categories;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.categories.CategoryResponse;
import com.blog.blog_literario.dto.categories.CreateCategoryRequest;
import com.blog.blog_literario.dto.categories.UpdateCategoryRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.utils.SlugUtils;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // ─── Mapper ────────────────────────────────────────────────────────────────
    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }

    // ─── Queries ────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(@NonNull Integer id) {
        return categoryRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la categoria con ID: " + id));
    }

    // ─── Commands ────────────────────────────────────────────────────────────────
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.findByName(request.name()).isPresent()) {
            throw new RuntimeException("La categoria ya existe");
        }

        //Create the category Slug
        String slug = SlugUtils.toSlug(request.name());

        Category newCategory = new Category(request.name(), slug);

        return toResponse(categoryRepository.save(newCategory));
    }

    public CategoryResponse updateCategory(@NonNull Integer id, UpdateCategoryRequest request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Categoría no encontrada con ID: " + id));

        String newSlug = SlugUtils.toSlug(request.name());

        // Avoid to create already existing slugs
        if (!existing.getSlug().equals(newSlug) && categoryRepository.existsBySlug(newSlug)) {
            throw new RuntimeException(
                    "El slug generado ya existe. Elige un nombre diferente.");
        }

        existing.setName(request.name());
        existing.setSlug(newSlug);

        return toResponse(categoryRepository.save(existing));
    }

    public void deleteCategory(@NonNull Integer id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "No se encontró la Categoría con ID: " + id);
        }
        categoryRepository.deleteById(id);
    }
}
