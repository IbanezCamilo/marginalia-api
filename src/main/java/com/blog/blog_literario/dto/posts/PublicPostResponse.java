package com.blog.blog_literario.dto.posts;

import java.time.LocalDateTime;
// This DTO is used for returning the post data to the public, it contains only the fields that are necessary for the public to see, it does not contain the categoryId or the authorId, instead it contains the categoryName and the authorName for better readability

public record PublicPostResponse(
        String title,
        String content,
        String slug,
        String AuthorName,
        String CategoryName,
        LocalDateTime createdAt
        ) {

}
