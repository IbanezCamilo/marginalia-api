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
import com.blog.blog_literario.repositories.PostRepository;
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
    private final PostRepository postRepository;

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
     * @throws IllegalStateException if a category with the same name or the generated slug already exists
     */
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        if (categoryRepository.findByName(request.name()).isPresent()) {
            throw new IllegalStateException("La categoria ya existe");
        }

        String slug = SlugUtils.toSlug(request.name());

        // Avoid creating a duplicate slug — two different names can produce the same slug
        if (categoryRepository.existsBySlug(slug)) {
            throw new IllegalStateException(
                    "El slug generado ya existe. Elige un nombre diferente.");
        }

        Category newCategory = new Category(request.name(), slug);

        return toResponse(categoryRepository.save(newCategory));
    }

    /**
     * Updates a category's name and regenerates its slug.
     *
     * @throws ResourceNotFoundException if no category exists with the given {@code id}
     * @throws IllegalStateException     if the new name or the new slug collides with another existing category
     */
    public CategoryResponse updateCategory(@NonNull Integer id, UpdateCategoryRequest request) {
        Category existing = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                "Categoría no encontrada con ID: " + id));

        // Avoid creating a duplicate name — only check if it's actually changing
        if (!existing.getName().equals(request.name()) && categoryRepository.findByName(request.name()).isPresent()) {
            throw new IllegalStateException("La categoria ya existe");
        }

        String newSlug = SlugUtils.toSlug(request.name());

        // Avoid creating a duplicate slug — two different names can produce the same slug
        if (!existing.getSlug().equals(newSlug) && categoryRepository.existsBySlug(newSlug)) {
            throw new IllegalStateException(
                    "El slug generado ya existe. Elige un nombre diferente.");
        }

        existing.setName(request.name());
        existing.setSlug(newSlug);

        return toResponse(categoryRepository.save(existing));
    }

    /**
     * @throws ResourceNotFoundException if no category exists with the given {@code id}
     * @throws IllegalStateException if posts still reference this category
     */
    public void deleteCategory(@NonNull Integer id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException(
                    "No se encontró la Categoría con ID: " + id);
        }

        long postCount = postRepository.countByCategoryId(id);
        if (postCount > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar la categoría: tiene " + postCount + " post(s) asociados");
        }

        categoryRepository.deleteById(id);
    }
}
