package com.blog.blog_literario.support;

import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.security.UserDetailsImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public final class TestSecurityFactory {

    public static Authentication asAuthor(Integer id) {
        return build(id, "author@test.com", "Test Author", "AUTHOR");
    }

    public static Authentication asAdmin(Integer id) {
        return build(id, "admin@test.com", "Test Admin", "ADMIN");
    }

    public static Authentication asReader(Integer id) {
        return build(id, "reader@test.com", "Test Reader", "READER");
    }

    public static Authentication asModerator(Integer id) {
        return build(id, "mod@test.com", "Test Moderator", "MODERATOR");
    }

    private static Authentication build(Integer id, String email, String name, String roleName) {
        Role role = new Role(roleName);
        User user = new User(id, name, email, role);
        UserDetailsImpl details = new UserDetailsImpl(user);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }
}
