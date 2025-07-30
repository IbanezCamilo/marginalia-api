package com.blog.blog_literario.services.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.categoriesDTO.categoryCreateDTO;
import com.blog.blog_literario.dto.categoriesDTO.categoryUpdateDTO;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.services.CategoryService;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository; // Inyección del Repositorio de Categorias

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll(); // Retorna todos los usuarios del Repositorio
    }

    @Override
    public Category getCategoryById(Integer id) {
        return categoryRepository.findById(id) // Verifica la existencia de la categoria
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la Categoria con ID: " + id)); // No
                                                                                                              // existe
                                                                                                              // la
                                                                                                              // categoria:
                                                                                                              // Lanza
                                                                                                              // Exepcion
    }

    @Override
    public Category createCategory(categoryCreateDTO dto){
        //Validar si la categoria ya existe
        if (categoryRepository.findByNombre(dto.getNombre()).isPresent()) {
            throw new RuntimeException("La categoria ya existe");
        }

        //Crear una nueva Categoria
        Category nuevaCategoria = new Category();
        nuevaCategoria.setNombre(dto.getNombre());
        nuevaCategoria.setSlug(dto.getSlug());

        //Guardar y retornar
        return categoryRepository.save(nuevaCategoria);
    }

    @Override
    public Category updateCategory(Integer id, categoryUpdateDTO dto) {
        // Verificar si la categoria Existe
        Category categoriaExistente = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con ID: " + id));

        // Actualizamos la categoria
        categoriaExistente.setNombre(dto.getNombre());
        categoriaExistente.setSlug(dto.getSlug());

        // Guardar y retornar
        return categoryRepository.save(categoriaExistente); // Actualiza y retorna la categoria
    }

    @Override
    public void deleteCategory(Integer id) {
        if (!categoryRepository.existsById(id)) // Verifica la existencia de la categoria
            throw new ResourceNotFoundException("No se encontró la Categoria con ID: " + id); // No existe la categoria:
                                                                                              // Lanza
                                                                                              // Exepcion
        categoryRepository.deleteById(id); // Existe la Categoria: Elimina la categoria
    }
}
