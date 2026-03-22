package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.dto.posts.UpdatePostRequest;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.general.ImageStorageServiceV2;
import com.blog.blog_literario.utils.SlugUtils;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class MyPostCommandService {

    private final ImageStorageServiceV2 imageStorageServiceV2;

    private final PostRepository postRepository;

    private final UserRepository userRepository;

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<MyPostResponse> list(Integer userId, Pageable pageable) {
        return postRepository
                .findByAuthorId(userId, pageable)
                .map(this::ToResponse);
    }

    @Transactional(readOnly = true)
    public MyPostResponse getById(Integer userId, Integer postId) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado con ID: " + postId));

        return ToResponse(post);
    }

    public MyPostResponse create(@NonNull Integer userId, CreatePostRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));

        Category category = categoryRepository.findById(request.categoryId()).orElseThrow(() -> new RuntimeException("Categoria no encontrada con ID: " + request.categoryId()));

        //Generate Slug
        String slug = SlugUtils.toSlug(request.title());
        //Validate Slug Uniqueness
        if (postRepository.existsBySlug(slug)) {
            throw new RuntimeException("El slug ya existe. Por favor, elige otro título.");
        }

        Post post = new Post(
                request.title(),
                request.content(),
                PostStatus.DRAFT, // default status for new posts
                slug,
                user,
                category
        );

        postRepository.save(post);
        return ToResponse(post);
    }

    public MyPostResponse update(Integer userId, Integer postId, UpdatePostRequest request) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado con ID: " + postId));

        post.setTitle(request.title());
        post.setContent(request.content());

        //Validate Slug Uniqueness if title changed
        PostStatus newStatus = PostStatus.valueOf(request.status());
        validateAuthorCanChangeStatus(post.getStatus(), newStatus);

        post.setStatus(newStatus);

        //Title changed: Rebuild Slug
        String newSlug = SlugUtils.toSlug(request.title());
        if (!post.getSlug().equals(newSlug)) {
            if (postRepository.existsBySlugAndIdNot(newSlug, postId)) {
                throw new RuntimeException("Ya existe un post con tal slug");
            }
            post.setSlug(newSlug);
        }

        return ToResponse(post);
    }

    // This method is specifically for changing status, it will have more strict rules than the general update method
    public MyPostResponse updateStatus(Integer userId, Integer postId, String newStatusStr) {

        //Verify if the post exist and belongs to the user
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado con ID: " + postId));

        // Conver String to Enum
        PostStatus newStatus;
        try {
            newStatus = PostStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Estado no valido: " + newStatusStr);
        }

        //Validate if the author can change to the new status
        validateAuthorCanChangeStatus(post.getStatus(), newStatus);

        //Execute changes and save
        post.setStatus(newStatus);
        postRepository.save(post);

        return ToResponse(post);
    }

    public void delete(@NonNull Integer userId, @NonNull Integer postId) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow();

        postRepository.delete(post);
    }

    public MyPostResponse uploadCoverImage(@NonNull Integer userId, @NonNull Integer postId, MultipartFile image) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado con id: " + postId));

        if (!post.getAuthor().getId().equals(userId)) {
            throw new RuntimeException("No tienes permisos para modificar este post");
        }

        if (image == null || image.isEmpty()) {
            throw new RuntimeException("No se proporciono ninguna imagen");
        }

        String imageUrl = imageStorageServiceV2.saveImage(image);
        post.setCoverImage(imageUrl);
        postRepository.save(post);

        return ToResponse(post);
    }

    private void validateAuthorCanChangeStatus(PostStatus currentStatus, PostStatus newStatus) {
        if (currentStatus == newStatus) {
            return; // No status change, no validation needed
        }
        //Only allow this list of status changes for authors
        boolean isAllowed = switch (currentStatus) {
            case DRAFT ->
                newStatus == PostStatus.PUBLISHED;
            case PUBLISHED ->
                newStatus == PostStatus.DRAFT;
            case REJECTED ->
                newStatus == PostStatus.DRAFT;
            case ARCHIVED ->
                false;
        };

        if (!isAllowed) {
            throw new IllegalStateException(
                    String.format("No puedes cambiar el estado de %s a %s. Solo administradores pueden hacer esto.",
                            currentStatus, newStatus)
            );
        }
    }

    public MyPostResponse ToResponse(Post post) {
        return new MyPostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getStatus().name(),
                post.getSlug(),
                post.getAuthor().getName(),
                post.getCategory().getId(),
                post.getCategory().getName(),
                post.getCoverImage(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
