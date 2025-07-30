package com.blog.blog_literario.dto.usersDTO;

import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import lombok.Data; // para getters, setters, toString etc...

@Data
public class userProfileUpdateDTO {
    @NotBlank(message="El nombre no puede estar vacio")
    private String nombre;
    private String descripcion;
    private MultipartFile fotoPerfil; //para archivos enviados desde una formulario
}
