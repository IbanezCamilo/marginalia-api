package com.blog.blog_literario.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "rol")
public class Rol {
    @Id // Indica que este campo es la clave primaria
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Generación automática del ID
    @Column(name = "id_rol") // Nombre de la columna en la base de datos
    private Integer idRol;

    @Column(name = "nombre", nullable = false)
    private String nombre;
}
