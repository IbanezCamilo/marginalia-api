package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.blog.blog_literario.dto.posts.CreatePostRequest;
import com.blog.blog_literario.dto.posts.MyPostResponse;
import com.blog.blog_literario.dto.posts.UpdatePostRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Category;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.CategoryRepository;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.StorageService;
import com.blog.blog_literario.utils.SlugUtils;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Author-facing CRUD service for posts.
 *
 * <p>Enforces author-level business rules:
 * <ul>
 *   <li>Authors may only read and write their own posts.</li>
 *   <li>Status transitions are restricted: DRAFT → PUBLISHED → DRAFT and REJECTED → DRAFT.
 *       Admins bypass these restrictions through {@link AdminPostModerationService}.</li>
 *   <li>Deleting a post also removes its cover image from storage.</li>
 * </ul>
 */
@Service
@Transactional
@RequiredArgsConstructor
public class MyPostCommandService {

    private final StorageService storageService;

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

    /**
     * Creates a new post for the given author. The slug is derived from the title
     * and must be globally unique.
     *
     * @throws RuntimeException if the generated slug already exists or the user/category is not found
     */
    public MyPostResponse create(@NonNull Integer userId, CreatePostRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + userId));

        Category category = categoryRepository.findById(request.categoryId()).orElseThrow(() -> new RuntimeException("Categoria no encontrada con ID: " + request.categoryId()));

        String slug = SlugUtils.toSlug(request.title());
        if (postRepository.existsBySlug(slug)) {
            throw new RuntimeException("El slug ya existe. Por favor, elige otro título.");
        }

        Post post = new Post(
                request.title(),
                request.content(),
                PostStatus.valueOf(request.status()),
                slug,
                user,
                category
        );

        postRepository.save(post);
        return ToResponse(post);
    }

    /**
     * Updates all mutable fields of a post. Rebuilds the slug when the title changes;
     * validates author-permitted status transitions.
     *
     * @throws RuntimeException if the post does not belong to the user, or if the new slug collides
     */
    public MyPostResponse update(Integer userId, Integer postId, UpdatePostRequest request) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado con ID: " + postId));

        post.setTitle(request.title());
        post.setContent(request.content());

        // Category changed
        if(request.categoryId() != null){
            Category category = categoryRepository.findById(request.categoryId()).orElseThrow(() -> new RuntimeException("Categoria no encontrada con ID: " + request.categoryId()));
            post.setCategory(category);
        }

        PostStatus newStatus = PostStatus.valueOf(request.status());
        validateAuthorCanChangeStatus(post.getStatus(), newStatus);

        post.setStatus(newStatus);

        // Title changed: rebuild slug to stay consistent with the title
        String newSlug = SlugUtils.toSlug(request.title());
        if (!post.getSlug().equals(newSlug)) {
            if (postRepository.existsBySlugAndIdNot(newSlug, postId)) {
                throw new RuntimeException("Ya existe un post con tal slug");
            }
            post.setSlug(newSlug);
        }

        return ToResponse(post);
    }

    /**
     * Changes only the status of a post, applying stricter transition rules than
     * {@link #update} (which also updates content fields at the same time).
     *
     * @throws RuntimeException      if the post does not belong to the user or status is invalid
     * @throws IllegalStateException if the requested transition is not permitted for authors
     */
    public MyPostResponse updateStatus(Integer userId, Integer postId, String newStatusStr) {

        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado con ID: " + postId));

        // Convert String to Enum
        PostStatus newStatus;
        try {
            newStatus = PostStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Estado no valido: " + newStatusStr);
        }

        validateAuthorCanChangeStatus(post.getStatus(), newStatus);

        post.setStatus(newStatus);
        postRepository.save(post);

        return ToResponse(post);
    }

    /**
     * Deletes the post and its cover image from storage.
     * Deleting the cover image first prevents orphaned files if the DB delete fails.
     *
     * @throws RuntimeException if the post does not belong to the user
     */
    public void delete(@NonNull Integer userId, @NonNull Integer postId) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new RuntimeException("Post no encontrado"));

        // Delete cover image before the post to avoid orphaned files on storage
        storageService.delete(post.getCoverImage());

        postRepository.delete(post);
    }

    public MyPostResponse deleteCoverImage(@NonNull Integer userId, @NonNull Integer postId) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + postId));

        storageService.delete(post.getCoverImage());
        post.setCoverImage(null);
        postRepository.save(post);

        return ToResponse(post);
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

        String imageUrl = storageService.save(image, post.getCoverImage());

        post.setCoverImage(imageUrl);
        postRepository.save(post);

        return ToResponse(post);
    }

    private void validateAuthorCanChangeStatus(PostStatus currentStatus, PostStatus newStatus) {
        if (currentStatus == newStatus) {
            return; // No status change, no validation needed
        }
        // Only these transitions are allowed for authors; admin-only changes go through AdminPostModerationService
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
                storageService.buildUrl(post.getCoverImage()),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
