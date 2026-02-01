package com.blog.blog_literario.controllers.posts;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.postRequestDTO;
import com.blog.blog_literario.dto.posts.postUpdateDTO;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.services.general.PostService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    @Autowired
    private PostService postService;

    @GetMapping // Método para obtener todos los posts
    public ResponseEntity<?> getAllPosts() {
        List<Post> posts = postService.getAllPosts();
        return ResponseEntity.ok(posts); // status 200 = OK
    }

    @GetMapping("/{id}") // Método para obtener un post por ID
    public ResponseEntity<?> getPostById(@PathVariable Integer id) {
        Post post = postService.getPostById(id);
        return ResponseEntity.ok(post); // status 200 = OK
    }

    // Método para crear un nuevo post
    @PostMapping("/create-post")
    public ResponseEntity<?> createPost(@Valid @RequestPart("postData") postRequestDTO dto,
            @RequestPart(value = "postImage", required = false) MultipartFile image, BindingResult result) {
        //Validacion de Errores DTO
        if (result.hasErrors()) {
            //Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + " : " + e.getDefaultMessage()) //Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); //devuelve un http 400 con la lista de errores
        }
        //Crear, Guardar y Retornar
        Post postCreado = postService.createPost(dto, image); // Envia y retorna datos al PostService
        return ResponseEntity.status(201).body(postCreado); // 201: Creado exitosamente
    }

    @PutMapping("/{id}") // Método para actualizar un post existente
    public ResponseEntity<?> updatePost(@PathVariable Integer id, @Valid @RequestBody postUpdateDTO dto, BindingResult result) {
        //Validacion de Errores DTO
        if (result.hasErrors()) {
            //Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + " : " + e.getDefaultMessage()) //Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); //devuelve un http 400 con la lista de errores
        }

        //Actualizar, Guardar y Retornar
        Post postActualizado = postService.updatePost(id, dto); // Envia y retorna datos al PostService
        return ResponseEntity.status(201).body(postActualizado); // status = 201: Guardado exitosamente 
    }

    // @DeleteMapping("/{id}") // Método para eliminar un post por ID
    // public ResponseEntity<?> deletePost(@PathVariable Integer id) {
    //     postService.deletePost(id);
    //     return ResponseEntity.noContent().build(); // 204 No content: Eliminado exitosamente
    // }
}
