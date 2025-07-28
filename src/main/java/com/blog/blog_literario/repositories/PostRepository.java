package com.blog.blog_literario.repositories;

import com.blog.blog_literario.entities.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Integer> {
        // Buscar por estado (publicada, borrador, etc.)
    List<Post> findByEstado(String estado);

    // Buscar por slug (único)
    Optional<Post> findBySlug(String slug);

    // Buscar todos los posts de un usuario
    List<Post> findByUsuario_IdUsuario(Integer idUsuario);

    // Buscar todos los posts de una categoría
    List<Post> findByCategoria_IdCategoria(Integer idCategoria);

    // Buscar por título (exacto)
    List<Post> findByTitulo(String titulo);

    // Buscar por fragmento de título (búsqueda parcial)
    List<Post> findByTituloContaining(String fragmento);

    // Verificar si existe un post con un slug específico
    boolean existsBySlug(String slug);
}
