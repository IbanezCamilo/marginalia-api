package com.blog.blog_literario.dto.users;

import com.blog.blog_literario.model.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data; // Importa Lombot para getters, setter, ToString etc... 

//DTO EXCLUSIVO DEL ADMIN PARA ACTUALIZAR USUARIOS
@Data
public class userUpdateDTO {
    @NotBlank(message="El nombre no puede estar vacio")
    private String nombre;
    @NotBlank(message="La contraseña es obligatoria")
    @Size(min = 8, message = "La contraseña debe ser de 8 caracteres o más")
    private String password;
    @NotBlank(message="El correo es obligatorio")
    @Email(message = "Correo no válido")
    private String email;
    @NotBlank(message="El rol es obligatorio")
    private Rol rol;
}
