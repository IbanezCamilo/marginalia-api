package com.blog.blog_literario.repositories;

import com.blog.blog_literario.entities.User; //Importa la entidad user
import org.springframework.data.jpa.repository.JpaRepository; //Importa JpaRepository para operaciones CRUD
import java.util.Optional; //Importa Optional para manejar valores que pueden ser nulos

public interface UserRepository extends JpaRepository<User, Integer> {
    
    // Método para encontrar un usuario por su email
    Optional<User> findByEmail(String email);
    
    // Método para verificar si un usuario existe por su email
    boolean existsByEmail(String email);
    
    // Método para verificar si un usuario existe por su nombre
    boolean existsByNombre(String nombre);

    // Método para encontrar un usuario por su nombre
    Optional<User> findByNombre(String nombre);
}
