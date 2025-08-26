package com.blog.blog_literario.dto.posts;

public record postRequestDTO(
        String titulo,
        String contenido,
        Integer idCategoria,
        String estado
){}
