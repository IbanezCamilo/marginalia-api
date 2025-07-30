package com.blog.blog_literario.services;

import java.util.List;

import com.blog.blog_literario.dto.categoriesDTO.categoryCreateDTO;
import com.blog.blog_literario.dto.categoriesDTO.categoryUpdateDTO;
import com.blog.blog_literario.model.Category;

public interface CategoryService {
    Category createCategory(categoryCreateDTO dto); //Método plantilla para crear una categoria
    Category updateCategory(Integer id, categoryUpdateDTO dto); //Método plantilla para actualizar una categoria
    void deleteCategory(Integer id); //Método plantilla para actualizar una categoria
    Category getCategoryById(Integer id); //Método plantilla para obtener una categoria
    List<Category> getAllCategories(); //Método plantilla para obtener todas las categorias
}
