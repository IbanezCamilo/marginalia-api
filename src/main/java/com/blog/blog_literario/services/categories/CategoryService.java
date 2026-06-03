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

/**
 * Admin-facing service for managing {@link Category} entities.
 *
 * <p>Handles the full CRUD lifecycle: listing, lookup, creation (with slug generation),
 * update, and deletion. All write operations require the caller to hold an ADMIN role,
 * enforced at the controller layer.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug()
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * @throws ResourceNotFoundException if no category exists with the given {@code id}
     */
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(@NonNull Integer id) {
        return categoryRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la categoria con ID: " + id));
    }

    /**
     * Creates a new category. The URL slug is derived automatically from the name.
     *
     * @throws RuntimeException if a category with the same name already exists
     */
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.findByName(request.name()).isPresent()) {
            throw new RuntimeException("La categoria ya existe");
        }

        String slug = SlugUtils.toSlug(request.name());

        Category newCategory = new Category(request.name(), slug);

        return toResponse(categoryRepository.save(newCategory));
    }

    /**
     * Updates a category's name and regenerates its slug.
     *
     * @throws ResourceNotFoundException if no category exists with the given {@code id}
     * @throws RuntimeException          if the new slug collides with an existing category
     */
    public CategoryResponse updateCategory(@NonNull Integer id, UpdateCategoryRequest request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Categoría no encontrada con ID: " + id));

        String newSlug = SlugUtils.toSlug(request.name());

        // Avoid creating a duplicate slug — two different names can produce the same slug
        if (!existing.getSlug().equals(newSlug) && categoryRepository.existsBySlug(newSlug)) {
            throw new RuntimeException(
                    "El slug generado ya existe. Elige un nombre diferente.");
        }

        existing.setName(request.name());
        existing.setSlug(newSlug);

        return toResponse(categoryRepository.save(existing));
    }

    /**
     * @throws ResourceNotFoundException if no category exists with the given {@code id}
     */
    public void deleteCategory(@NonNull Integer id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "No se encontró la Categoría con ID: " + id);
        }
        categoryRepository.deleteById(id);
    }
}
