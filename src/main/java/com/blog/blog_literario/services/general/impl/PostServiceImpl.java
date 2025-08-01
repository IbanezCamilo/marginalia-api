package com.blog.blog_literario.services.general.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.blog.blog_literario.dto.posts.postCreateDTO;
import com.blog.blog_literario.dto.posts.postUpdateDTO;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.PostService;

@Service
public class PostServiceImpl implements PostService{
    @Autowired
    private PostRepository postRepository; // Inyección del Repositorio de Posts
    @Autowired
    private UserRepository userRepository; //Inyección del Repositorio de Usuarios
    @Autowired
    private CategoryRepository categoryRepository; // Inyección del Repositorio de Categorias

    @Override
    public Post createPost(postCreateDTO dto) {
        // Usuario creador del post
        User usuario = userRepository.findById(dto.getIdUsuario())
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + dto.getIdUsuario()));
    
        // Categoria del post
        Category categoria = categoryRepository.findById(dto.getIdCategoria())
            .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con ID: " + dto.getIdCategoria()));
        
        //Crear nuevo post
        Post nuevoPost = new Post();
            nuevoPost.setTitulo(dto.getTitulo());
            nuevoPost.setContenido(dto.getContenido());
            nuevoPost.setEstado(dto.getEstado());
            nuevoPost.setSlug(dto.getSlug());
            nuevoPost.setImagenPortada(dto.getImagenPortada());
            nuevoPost.setFechaCreacion(LocalDateTime.now());
            nuevoPost.setFechaActualizacion(LocalDateTime.now());
            nuevoPost.setUsuario(usuario); // Crear Relacion con Usuario
            nuevoPost.setCategoria(categoria); // Crear Relacion con Categoria

            //Guardar y Retornar
            return postRepository.save(nuevoPost); // 201: Creado exitosamente
    }

    @Override
    public Post updatePost(Integer id, postUpdateDTO dto){
        // Verificar existencia del post
        Post postExistente = postRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + id));
        
        //Actualiza el contenido del post    
        postExistente.setTitulo(dto.getTitulo());
        postExistente.setContenido(dto.getContenido());
        postExistente.setEstado(dto.getEstado());
        postExistente.setSlug(dto.getSlug());
        postExistente.setImagenPortada(dto.getImagenPortada());
        postExistente.setFechaActualizacion(LocalDateTime.now());
        
        //Actualiza la Relación con Categoria
        if (dto.getIdCategoria() != null) {
            Category categoria = categoryRepository.findById(dto.getIdCategoria())// Verificar existencia de la categoría
                .orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con ID: " + dto.getIdCategoria()));
            postExistente.setCategoria(categoria); // Guarda la nueva categoria
        }

        return postRepository.save(postExistente); // Guarda y retorna el post actualizado
    }

    @Override
    public void deletePost(Integer id){
        if (!postRepository.existsById(id)) //Verifica la existencia del Post
            throw new ResourceNotFoundException("No se encontró el Post con ID: " + id); // No existe el post: Lanza Exepcion
        
        postRepository.deleteById(id); // Existe el post: Elimina el Post
    }

    @Override
    public Post getPostById(Integer id){
        return postRepository.findById(id) //Verifica la existencia del Post
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró el Post con ID: " + id)); // No existe el post: Lanza Exepcion
    }

    @Override
    public List<Post> getAllPosts(){
        return postRepository.findAll();
    }
}
