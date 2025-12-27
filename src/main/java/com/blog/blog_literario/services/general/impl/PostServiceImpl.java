package com.blog.blog_literario.services.general.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.postRequestDTO;
import com.blog.blog_literario.dto.posts.postUpdateDTO;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.PostService;
import com.blog.blog_literario.utils.SlugUtils;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository; // Inyección del Repositorio de Posts
    @Autowired
    private UserRepository userRepository; // Inyección del Repositorio de Usuarios
    @Autowired
    private CategoryRepository categoryRepository;

    @Override
    public Post createPost(postRequestDTO dto, MultipartFile image) {
        // Obtener email del usuario
        String emailAuthor = SecurityContextHolder.getContext().getAuthentication().getName();

        // Usuario creador del post
        User author = userRepository.findByEmail(emailAuthor)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con correo: " + emailAuthor));

        // Categoria del post
        Category category = categoryRepository.findById(dto.categoryId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Categoria no encontrada con ID: " + dto.categoryId()));

        // Creación del Slug
        String postSlug = SlugUtils.toSlug(dto.title());

        // Crear nuevo post
        Post newPost = new Post(dto.title(), dto.content(), dto.estatus(), postSlug, author, category);
        newPost.setCreatedAt(LocalDateTime.now());
        newPost.setUpdatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            // Generar el nombre del archivo a partir de un identificador aleatorio
            // y el nombre original del archivo
            String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
            Path imagePath = Paths.get("ImgTest").resolve(fileName);
            try {
                // Crea la carpeta si no existe
                Files.createDirectories(imagePath.getParent());
                // param1: Flujo de datos del archivo
                // param2: Copia el contenido de imagePath
                // param3: Si existe un archivo con el mismo nombre lo sobreescribe
                Files.copy(image.getInputStream(), imagePath, StandardCopyOption.REPLACE_EXISTING);
                // Guarda la ruta relativa
                newPost.setCoverImage("/ImgTest/" + fileName);
            } catch (IOException e) {
                throw new RuntimeException("Error al guardar la imagen" + e);
            }
        } else {
            newPost.setCoverImage("/ImgTest/" + "default-image.png"); // Ruta por defecto si no se proporciona una imagen
        }

        // Guardar y Retornar
        return postRepository.save(newPost); // 201: Creado exitosamente
    }

    @Override
    public Post updatePost(Integer id, postUpdateDTO dto) {
        // Verificar existencia del post
        Post existingPost = postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + id));

        // Actualiza el contenido del post
        existingPost.setTitle(dto.getTitle());
        existingPost.setContent(dto.getContent());
        existingPost.setStatus(dto.getStatus());
        existingPost.setSlug(dto.getSlug());
        existingPost.setCoverImage(dto.getCoverImage());
        existingPost.setUpdatedAt(LocalDateTime.now());

        // Actualiza la Relación con Categoria
        if (dto.getCategoryId() != null) {
            Category category = categoryRepository.findById(dto.getCategoryId())// Verificar existencia de la
                    // categoría
                    .orElseThrow(() -> new ResourceNotFoundException(
                    "Categoria no encontrada con ID: " + dto.getCategoryId()));
            existingPost.setCategory(category); // Guarda la nueva categoria
        }

        return postRepository.save(existingPost); // Guarda y retorna el post actualizado
    }

    @Override
    public void deletePost(Integer id) {
        if (!postRepository.existsById(id)) // Verifica la existencia del Post
        {
            throw new ResourceNotFoundException("No se encontró el Post con ID: " + id); // No existe el post: Lanza
        }                                                                                         // Exepcion

        postRepository.deleteById(id); // Existe el post: Elimina el Post
    }

    @Override
    public Post getPostById(Integer id) {
        return postRepository.findById(id) // Verifica la existencia del Post
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el Post con ID: " + id)); // No existe
        // el post:
        // Lanza
        // Exepcion
    }

    @Override
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }
}
