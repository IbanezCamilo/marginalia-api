package com.blog.blog_literario.dto.posts;

import java.time.LocalDateTime;

public record MyPostResponse(
        Integer id,
        String title,
        String content,
        String status,
        String slug,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
        ) {}
