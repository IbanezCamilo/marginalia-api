package com.blog.blog_literario.dto.posts;

public record UpdatePostRequest(
        String title,
        String content,
        Integer categoryId,
        String status
        ) {

}
