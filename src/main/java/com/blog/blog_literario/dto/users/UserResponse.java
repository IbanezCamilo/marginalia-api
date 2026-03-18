package com.blog.blog_literario.dto.users;

import com.blog.blog_literario.model.Role;

public record UserResponse(
        Integer id,
        String name,
        String email,
        Role role
        ) {

}
