package com.blog.blog_literario.dto.posts;

import java.time.LocalDateTime;

public record MyPostResponse(
        Integer id,
        String title,
        String content,
        String status,
        String slug,
        String authorName,
        Integer categoryId,
        String categoryName,
        String coverImage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
        ) {

}
