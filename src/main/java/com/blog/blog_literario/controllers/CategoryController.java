package com.blog.blog_literario.controllers;

import com.blog.blog_literario.dto.categoryCreateDTO;
import com.blog.blog_literario.dto.categoryUpdateDTO;
import com.blog.blog_literario.entities.Category;
import com.blog.blog_literario.services.CategoryService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired; // Inyección de dependencias
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*; // Anotaciones para crear controladores REST

import java.util.List;
//import java.util.Optional;

@RestController // Indica que esta clase es un controlador REST
@RequestMapping("/api/categories") // Define la ruta base para las peticiones a este controlador

public class CategoryController {

    @Autowired
    private CategoryService categoryService; // Inyeccion de repositorio de Category

    @GetMapping // Método para obtener todos los posts
    public ResponseEntity<?> getAllCategories() {
         List<Category> categorias = categoryService.getAllCategories();
         return ResponseEntity.ok(categorias); // status 200 = OK
    }

    @GetMapping("/{id}") // Método para obtener un post por ID
    public ResponseEntity<?> getCategoryById(@PathVariable Integer id) {
        Category categoria = categoryService.getCategoryById(id);
        return ResponseEntity.ok(categoria); // status 200 = OK
    }

    @PostMapping // Método para crear una nueva categoria
    public ResponseEntity<?> createCategory(@Valid @RequestBody categoryCreateDTO dto, BindingResult result) {
        // Validacion de Errores DTO
        if (result.hasErrors()) {
            // Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + ":" + e.getDefaultMessage()) // Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); // devuelve un http 400 con la lista de errores
        }

        // Guardar y retornar
        Category categoriaCreada = categoryService.createCategory(dto);
        return ResponseEntity.status(201).body(categoriaCreada); // status 201 : Guardado existosamente
    }

    @PutMapping("/{id}") // Método para actualizar una categoria
    public ResponseEntity<?> updateCategory(@PathVariable Integer id, @Valid @RequestBody categoryUpdateDTO dto,
            BindingResult result) {
        // Validacion de Errores DTO
        if (result.hasErrors()) {
            // Si hay errores de validación, captura y devuelve una lista
            var errores = result.getFieldErrors()
                    .stream() // Inicia el flujo para recorrer la lista
                    .map(e -> e.getField() + ":" + e.getDefaultMessage()) // Estructura los errores en un string
                    .toList(); // Devuelve la lista de mensajes como strings
            return ResponseEntity.badRequest().body(errores); // devuelve un http 400 con la lista de errores
        }

        // Actualizar, Guardar y Retornar
        Category categoriaActualizada = categoryService.updateCategory(id, dto); // Envia y retorna datos al CategoryService
        return ResponseEntity.status(201).body(categoriaActualizada); // status = 201: Guardado exitosamente
    }

    @DeleteMapping("/{id}") // Método para eliminar un post por ID
    public ResponseEntity<?> deleteCategory(@PathVariable Integer id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build(); // 204 No content: Eliminado exitosamente
    }
}
