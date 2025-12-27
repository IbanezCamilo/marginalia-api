package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {

    // Método para encontrar un rol por su nombre
    Optional<Role> findByName(String name);
}
