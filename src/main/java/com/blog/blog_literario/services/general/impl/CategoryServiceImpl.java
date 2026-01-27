package com.blog.blog_literario.services.general.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.categories.categoryCreateDTO;
import com.blog.blog_literario.dto.categories.categoryUpdateDTO;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.services.general.CategoryService;

import lombok.NonNull;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryRepository categoryRepository; // Inyección del Repositorio de Categorias

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public Category getCategoryById(@NonNull Integer id) {
        return categoryRepository.findById(id) // Verifica la existencia de la categoria
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la Categoria con ID: " + id)); // No
        // existe
        // la
        // categoria:
        // Lanza
        // Exepcion
    }

    @Override
    public Category createCategory(categoryCreateDTO dto) {
        //Validar si la categoria ya existe
        if (categoryRepository.findByName(dto.getName()).isPresent()) {
            throw new RuntimeException("La categoria ya existe");
        }

        //Crear una nueva Categoria
        Category newCategory = new Category(dto.getName(), dto.getSlug());

        //Guardar y retornar
        return categoryRepository.save(newCategory);
    }

    @Override
    public Category updateCategory(@NonNull Integer id, categoryUpdateDTO dto) {
        // Verificar si la categoria Existe
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con ID: " + id));

        // Actualizamos la categoria
        existingCategory.setName(dto.getName());
        existingCategory.setSlug(dto.getSlug());

        // Guardar y retornar
        return categoryRepository.save(existingCategory); // Actualiza y retorna la categoria
    }

    @Override
    public void deleteCategory(@NonNull Integer id) {
        if (!categoryRepository.existsById(id)) // Verifica la existencia de la categoria
        {
            throw new ResourceNotFoundException("No se encontró la Categoria con ID: " + id); // No existe la categoria:
        }                                                                                              // Lanza
        // Exepcion
        categoryRepository.deleteById(id); // Existe la Categoria: Elimina la categoria
    }
}
