package com.blog.blog_literario.entities;

import jakarta.persistence.*; // Contiene todas las anotaciones de JPA
import lombok.Data;// Lombok para generar getters y setters automáticamente

@Entity // Indica que esta clase es una entidad JPA
@Data // Lombok generará getters, setters, toString, equals y hashCode
@Table(name = "usuario") // Nombre de la tabla en la base de datos
public class User {
    
    @Id // Indica que este campo es la clave primaria
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Generación automática del ID
    @Column(name = "id_usuario") // Nombre de la columna en la base de datos
    private Integer idUsuario;

    @Column(name = "nombre", nullable = false) // Columna no nula y única
    private String nombre;

    @Column(name="password", nullable = false, length = 255) // Columna no nula
    private String password;

    @Column(name = "email", nullable = false, unique = true) // Columna no nula
    private String email;

    @Column(name = "rol", nullable = false) // Columna no nula
    private String rol; // Rol del usuario (ej. ADMIN, USER)

}
