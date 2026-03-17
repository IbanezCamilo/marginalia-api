package com.blog.blog_literario.dto.profile;

public record UserProfileResponse(
        Integer id,
        String name,
        String email,
        String description,
        String profilePicture,
        String role
) {

}
