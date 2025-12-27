package com.blog.blog_literario.dto.users;

import com.blog.blog_literario.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// DTO EXCLUSIVE FOR ADMIN TO CREATE USERS
@Data
public class userCreateDTO {

    @NotBlank(message = "El nombre no puede estar vacio")
    private String name;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe ser de 8 caracteres o más")
    private String password;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo no válido")
    private String email;

    @NotBlank(message = "El rol es obligatorio")
    private Role role;
}
