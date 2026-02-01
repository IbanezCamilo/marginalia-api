package com.blog.blog_literario.dto.posts;

public record postRequestDTO(
        String title,
        String content,
        Integer categoryId,
        String status
        ) {

}
