package com.blog.blog_literario.dto.posts;

import java.math.BigDecimal;
import java.time.LocalDateTime;
// This DTO is used for returning the post data to the public, it contains only the fields that are necessary for the public to see, it does not contain the categoryId or the authorId, instead it contains the categoryName and the authorName for better readability

public record PublicPostResponse(
        String title,
        String content,
        String slug,
        Integer authorId,
        String authorName,
        String authorDescription,
        String authorProfilePicture,
        String categoryName,
        String categorySlug,
        String coverImage,
        BigDecimal focalX,
        BigDecimal focalY,
        LocalDateTime createdAt,
        boolean featured
        ) {

}
