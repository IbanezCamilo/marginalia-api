package com.blog.blog_literario.dto.users;

import java.time.LocalDateTime;

import com.blog.blog_literario.dto.roles.RoleResponse;

public record UserResponse(
        Integer id,
        String name,
        String email,
        RoleResponse role,
        LocalDateTime createdAt
) {
}
