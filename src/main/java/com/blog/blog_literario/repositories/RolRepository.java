package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.blog.blog_literario.model.Rol;

import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {
    // Método para encontrar un rol por su nombre
    Optional<Rol> findByNombre(String nombre);
}
