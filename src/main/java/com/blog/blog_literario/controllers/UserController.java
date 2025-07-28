package com.blog.blog_literario.controllers;

import com.blog.blog_literario.dto.userCreateDTO;
import com.blog.blog_literario.dto.userUpdateDTO;
import com.blog.blog_literario.entities.User;
import com.blog.blog_literario.services.UserService;

import jakarta.validation.Valid; //Jakarta para validaciones

import org.springframework.beans.factory.annotation.Autowired; // Inyección de dependencias
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*; // Anotaciones para crear controladores REST

import java.util.List;

@RestController // Indica que esta clase es un controlador REST
@RequestMapping("/api/users") // Define la ruta base para las peticiones a este controlador
public class UserController {
    
    @Autowired
    private UserService userSevice; //Inyección del Repositorio de Usuarios

    @GetMapping // Método para obtener todos los usuarios
    public ResponseEntity<?> getAllUsers(){
         List<User> usuarios = userSevice.getAllUsers();
         return ResponseEntity.ok(usuarios); // status 200 = OK
    }

    @GetMapping ("/{id}") //Método para obtener usuario por id
    public ResponseEntity<?> getUserById(@PathVariable Integer id) {
        User usuario = userSevice.getUserById(id);
        return ResponseEntity.ok(usuario); // status 200 = OK
    }    

    @PostMapping // Método para crear un nuevo usuario
    public ResponseEntity<?> createUser(@Valid @RequestBody userCreateDTO dto, BindingResult result){
        //Validacion de Errores DTO
        if(result.hasErrors()){
            //Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                .stream() // Inicia el flujo para recorrer la lista
                .map(e -> e.getField() + ":" + e.getDefaultMessage()) //Estructura los errores en un string
                .toList(); // Devuelve la lista de mensajes como strings
                return ResponseEntity.badRequest().body(errores); //devuelve un http 400 con la lista de errores
        }
        //Guardar y retornar
        User usuarioCreado = userSevice.createUser(dto); //Envia y retorna datos al userService
        return ResponseEntity.status(201).body(usuarioCreado); // status = 201: Guardado exitosamente
    }

    @PutMapping("/{id}") // Método para actualizar un usuario existente
    public ResponseEntity<?> updateUser(@PathVariable Integer id, @Valid @RequestBody userUpdateDTO dto, BindingResult result){
        //Validacion de Errores
        if(result.hasErrors()){
            //Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                .stream() // Inicia el flujo para recorrer la lista
                .map(e -> e.getField() + ":" + e.getDefaultMessage()) //Estructura los errores en un string
                .toList(); // Devuelve la lista de mensajes como strings
                return ResponseEntity.badRequest().body(errores); //devuelve un http 400 con la lista de errores
        }

        //Guardar y retornar
        User usuarioActualizado = userSevice.updateUser(id, dto); //Envia y retorna datos al userService
        return ResponseEntity.status(201).body(usuarioActualizado); // 201: Guardado correctamente
    }

    @DeleteMapping("/{id}") // Método para eliminar un usuario por ID
    public ResponseEntity<?> deleteUser(@PathVariable Integer id) {
        userSevice.deleteUser(id);
        return ResponseEntity.noContent().build(); // 204 No content: Eliminado exitosamente
    }
}
