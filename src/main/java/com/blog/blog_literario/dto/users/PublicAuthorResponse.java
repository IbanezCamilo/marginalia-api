package com.blog.blog_literario.dto.users;

public record PublicAuthorResponse(
        Integer id,
        String name,
        String description,
        String profilePicture
        ) {

}
