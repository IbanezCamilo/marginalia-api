package com.blog.blog_literario.repositories;

import com.blog.blog_literario.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // Método para encontrar una categoría por su nombre
    Optional<Category> findByNombre(String nombre);

    // Método para verificar si una categoría existe por su nombre
    boolean existsByNombre(String nombre);

    // Método para encontrar una categoría por su slug
    Optional<Category> findBySlug(String slug);

    // Método para verificar si una categoría existe por su slug
    boolean existsBySlug(String slug);
}
