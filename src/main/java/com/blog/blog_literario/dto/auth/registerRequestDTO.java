package com.blog.blog_literario.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data; // Importa Lombot para getters, setter, ToString etc... 

@Data
public class registerRequestDTO {
    @NotBlank(message="El nombre no puede estar vacio")
    private String nombre;
    @NotBlank(message="La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe ser de 8 caracteres o más")
    private String password;
    @NotBlank(message="El correo es obligatorio")
    @Email(message = "Correo no válido")
    private String email;
}