package com.blog.blog_literario.services.general;

import java.util.List;

import com.blog.blog_literario.dto.categories.categoryCreateDTO;
import com.blog.blog_literario.dto.categories.categoryUpdateDTO;
import com.blog.blog_literario.model.Category;

public interface CategoryService {
    Category createCategory(categoryCreateDTO dto);
    Category updateCategory(Integer id, categoryUpdateDTO dto); 
    void deleteCategory(Integer id); 
    Category getCategoryById(Integer id); 
    List<Category> getAllCategories(); 
}
