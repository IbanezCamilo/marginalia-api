package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Category;

import java.util.Optional;

/**
 * Repository for {@link Category} entities. Provides lookups by name and slug
 * used for uniqueness checks during creation and updates.
 */
public interface CategoryRepository extends JpaRepository<Category, Integer> {

    /** Used to prevent duplicate category names. */
    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    /** Used by public endpoints and slug-based lookups. */
    Optional<Category> findBySlug(String slug);

    /** Used during updates to prevent slug collisions. */
    boolean existsBySlug(String slug);
}
