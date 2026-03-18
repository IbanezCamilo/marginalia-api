package com.blog.blog_literario.repositories;

import org.springframework.data.jpa.repository.JpaRepository; //Importa JpaRepository para operaciones CRUD

import com.blog.blog_literario.model.User;

import java.util.Optional; //Importa Optional para manejar valores que pueden ser nulos

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByName(String name);

    Optional<User> findByName(String name);
}
