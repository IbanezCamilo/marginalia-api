package com.blog.blog_literario.services.general;

import java.util.List;

import com.blog.blog_literario.dto.posts.postCreateDTO;
import com.blog.blog_literario.dto.posts.postUpdateDTO;
import com.blog.blog_literario.model.Post;

public interface PostService {
    Post createPost(postCreateDTO dto); // Método plantilla para crear un Post
    Post updatePost(Integer id, postUpdateDTO dto); // Método plantilla para actualizar un Post
    void deletePost(Integer id); //Método plantilla para eliminar un Post
    Post getPostById(Integer id); // Método plantilla para obtener un Post por ID
    List<Post> getAllPosts(); // Método plantilla para obtener Todos los Posts
}
