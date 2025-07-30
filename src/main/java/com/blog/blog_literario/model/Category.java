package com.blog.blog_literario.model;

import jakarta.persistence.*; // Contiene todas las anotaciones de JPA
import lombok.Data; // Lombok para generar getters y setters automáticamente

@Entity // Indica que esta clase es una entidad JPA
@Data
@Table(name = "categoria") // Nombre de la tabla en la base de datos
public class Category {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Generación automática del ID
    @Column(name = "id_categoria") // Nombre de la columna en la base de datos
    private Integer idCategoria;

    @Column(name = "nombre", nullable = false, unique = true) // Columna no nula y única
    private String nombre;

    @Column(name = "slug", nullable =  false, unique = true) //
    private String slug; // Slug para URL amigable
}
