package com.blog.blog_literario.services.general.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.postRequestDTO;
import com.blog.blog_literario.dto.posts.postUpdateDTO;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.ImageStorageService;
import com.blog.blog_literario.services.general.PostService;
import com.blog.blog_literario.utils.SlugUtils;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ImageStorageService imageStorageService;

    @Override
    public Post createPost(postRequestDTO dto, MultipartFile image) {
        String emailAuthor = SecurityContextHolder.getContext().getAuthentication().getName();

        User author = userRepository.findByEmail(emailAuthor)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con correo: " + emailAuthor));

        Category category = categoryRepository.findById(dto.categoryId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Categoria no encontrada con ID: " + dto.categoryId()));

        // Creación del Slug
        String postSlug = SlugUtils.toSlug(dto.title());

        // Crear nuevo post
        Post newPost = new Post(dto.title(), dto.content(), dto.status(), postSlug, author, category);

        newPost.setCreatedAt(LocalDateTime.now());
        newPost.setUpdatedAt(LocalDateTime.now());

        if (image != null && !image.isEmpty()) {
            String imageUrl = imageStorageService.saveImage(image);
            newPost.setCoverImage(imageUrl);
        } else {
            //Default image
            newPost.setCoverImage("/api/images/default-image.png");
        }

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
        existingPost.setStatus(PostStatus.valueOf(dto.getStatus()));
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

        return postRepository.save(existingPost);
    }

    // @Override
    // public void deletePost(Integer id) {
    //     if (!postRepository.existsById(id)) {
    //         throw new ResourceNotFoundException("No se encontró el Post con ID: " + id);
    //     }
    //     postRepository.deleteById(id);
    // }
    @Override
    public Post getPostById(Integer id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el Post con ID: " + id));
    }

    @Override
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }
}
