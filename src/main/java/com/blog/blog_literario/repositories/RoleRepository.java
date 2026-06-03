package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Role;

import java.util.Optional;

/**
 * Repository for {@link Role} entities. Roles are seeded at startup and are
 * looked up by name throughout the application.
 */
public interface RoleRepository extends JpaRepository<Role, Integer> {

    /**
     * Finds a role by its name constant (e.g. {@code "READER"}, {@code "ADMIN"}).
     * Used by authentication, registration, and admin role-assignment flows.
     */
    Optional<Role> findByName(String name);
}
