package com.blog.blog_literario.dto.profile;

import java.time.LocalDateTime;

public record UserProfileResponse(
        Integer id,
        String name,
        String email,
        String description,
        String profilePicture,
        String role,
        LocalDateTime createdAt
        ) {

}
