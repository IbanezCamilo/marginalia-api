package com.blog.blog_literario.controllers.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.blog.blog_literario.dto.categories.CreateCategoryRequest;
import com.blog.blog_literario.dto.categories.UpdateCategoryRequest;
import com.blog.blog_literario.services.categories.CategoryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Admin endpoints for category management. All routes require {@code ROLE_ADMIN}
 * (enforced in {@link com.blog.blog_literario.config.SecurityConfig}).
 */
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    /** Creates a new category. Returns 400 with field-error details on validation failure. */
    @PostMapping
    public ResponseEntity<?> createCategory(
            @Valid @RequestBody CreateCategoryRequest dto,
            BindingResult result) {

        if (result.hasErrors()) {
            List<String> errors = result.getFieldErrors()
                    .stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .toList();

            return ResponseEntity.badRequest().body(errors);
        }

        return ResponseEntity.status(201).body(categoryService.createCategory(dto));
    }

    /** Updates a category's name and regenerates its slug. Returns 400 on validation failure. */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateCategory(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCategoryRequest dto,
            BindingResult result) {

        if (result.hasErrors()) {
            List<String> errors = result.getFieldErrors()
                    .stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .toList();
            return ResponseEntity.badRequest().body(errors);
        }

        return ResponseEntity.ok(categoryService.updateCategory(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
