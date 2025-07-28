package com.blog.blog_literario.entities;

import java.time.LocalDateTime;
import jakarta.persistence.*; // Contiene todas las anotaciones de JPA
import lombok.Data; // Lombok para generar getters y setters automáticamente

@Entity // Indica que esta clase es una entidad JPA
@Data
@Table(name = "Entrada") // Nombre de la tabla en la base de datos
public class Post {

    @Id // Indica que este campo es la clave primaria
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Generación automática del ID
    @Column(name="id_entrada")
    private Integer idEntrada;

    @Column(name="titulo", nullable = false) // Columna no nula
    private String titulo;

    @Column(name = "contenido", columnDefinition = "LONGTEXT") 
    private String contenido;
    
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;
    
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "estado", nullable = false)
    private String estado;

    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    @Column(name = "imagen_portada")
    private String imagenPortada; // ruta o url

    //Relaciones//
    @ManyToOne  /*Un usuario puede tener muchos posts, un post pertenece a un usuario */
    @JoinColumn(name="id_usuario", nullable = false)
    private User usuario; // Relación con el usuario del post

    @ManyToOne /*Una categoria puede estar en muchos posts, un post debe tener una categoria */
    @JoinColumn(name="id_categoria", nullable = false)
    private Category categoria; // Relación con la categoría del post
}
