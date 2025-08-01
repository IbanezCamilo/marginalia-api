package com.blog.blog_literario.dto.profile;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import lombok.Data; // para getters, setters, toString etc...

@Data
public class userProfileUpdateDTO {
    @NotBlank(message="El nombre no puede estar vacio")
    private String nombre;
    private String descripcion;
    private String fotoPerfil; // URL de la foto de perfil TEMPORALMENTE
    // Comentado para evitar errores de compilación, en un futuro se implementará la subida de archivos
    //private MultipartFile fotoPerfil; //para archivos enviados desde una formulario
}
