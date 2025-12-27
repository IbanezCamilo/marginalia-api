package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Post;

import java.util.Optional;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Integer> {
    // Buscar por estado (publicada, borrador, etc.)

    List<Post> findByStatus(String status);

    Optional<Post> findBySlug(String slug);

    List<Post> findByAuthor_Id(Integer authorId);

    List<Post> findByCategory_Id(Integer categoryId);

    List<Post> findByTitle(String title);

    List<Post> findByTitleContaining(String fragment);

    boolean existsBySlug(String slug);
}
