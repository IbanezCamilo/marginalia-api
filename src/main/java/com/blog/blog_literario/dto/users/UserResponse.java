package com.blog.blog_literario.dto.users;

import java.time.LocalDateTime;

import com.blog.blog_literario.model.Role;

public record UserResponse(
        Integer id,
        String name,
        String email,
        Role role,
        LocalDateTime createdAt
        ) {

}
