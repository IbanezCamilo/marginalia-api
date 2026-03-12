package com.blog.blog_literario.dto.posts;

public record CreatePostRequest(
        String title,
        String content,
        Integer categoryId,
        String status
        ) {

}
