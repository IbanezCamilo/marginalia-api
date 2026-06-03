package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
