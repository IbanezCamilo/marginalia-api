package com.blog.blog_literario.dto.admin;

import java.time.LocalDateTime;

public record AdminPostResponse(
        Integer id,
        String title,
        String slug,
        String status,
        String statusDisplayName,
        String authorName,
        String authorEmail,
        String categoryName,
        String coverImage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

}
