package com.blog.blog_literario.dto.posts;

import java.time.LocalDateTime;

public record PublicPostResponse(
    String title,
    String content,
    String slug,
    String AuthorName,
    String CategoryName,
    LocalDateTime createdAt
) {
}
