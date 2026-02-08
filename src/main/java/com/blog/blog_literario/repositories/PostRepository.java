package com.blog.blog_literario.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Post;

public interface PostRepository extends JpaRepository<Post, Integer> {

    //Public --Read Only--
    Page<Post> findByStatusIgnoreCase(String status, Pageable pageable);

    Optional<Post> findBySlugAndStatusIgnoreCase(String slug, String status);

    //Private --Legacy--
    List<Post> findByAuthor_Id(Integer authorId);

    List<Post> findByCategory_Id(Integer categoryId);

    List<Post> findByTitleContaining(String fragment);

    boolean existsBySlug(String slug);
}
