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
import com.blog.blog_literario.services.admin.AdminPostModerationService;
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
 *   <li>Status transitions are restricted: DRAFT → PUBLISHED → DRAFT and REJECTED → DRAFT,
 *       and a REJECTED → DRAFT resubmission is rejected once the post is permanently
 *       blocked (3 accumulated rejections; see {@link com.blog.blog_literario.model.Post#canBeResubmitted()}).
 *       Moderators and admins bypass these restrictions through
 *       {@link com.blog.blog_literario.services.moderator.ModeratorPostService} and
 *       {@link AdminPostModerationService} respectively.</li>
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
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public MyPostResponse getById(Integer userId, Integer postId) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + postId));

        return toResponse(post);
    }

    /**
     * Creates a new post for the given author. The slug is derived from the title
     * and must be globally unique.
     *
     * @throws ResourceNotFoundException if the user or category is not found
     * @throws IllegalStateException     if the generated slug already exists
     */
    public MyPostResponse create(@NonNull Integer userId, CreatePostRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + userId));

        Category category = categoryRepository.findById(request.categoryId()).orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con ID: " + request.categoryId()));

        String slug = SlugUtils.toSlug(request.title());
        if (postRepository.existsBySlug(slug)) {
            throw new IllegalStateException("El slug ya existe. Por favor, elige otro título.");
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
        return toResponse(post);
    }

    /**
     * Updates all mutable fields of a post. Rebuilds the slug when the title changes;
     * validates author-permitted status transitions.
     *
     * @throws ResourceNotFoundException if the post or category does not exist (or the post does not belong to the user)
     * @throws IllegalStateException     if the new slug collides with an existing post
     */
    public MyPostResponse update(Integer userId, Integer postId, UpdatePostRequest request) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + postId));

        post.setTitle(request.title());
        post.setContent(request.content());

        // Category changed
        if(request.categoryId() != null){
            Category category = categoryRepository.findById(request.categoryId()).orElseThrow(() -> new ResourceNotFoundException("Categoria no encontrada con ID: " + request.categoryId()));
            post.setCategory(category);
        }

        PostStatus newStatus = PostStatus.valueOf(request.status());
        validateAuthorCanChangeStatus(post.getStatus(), newStatus);

        post.setStatus(newStatus);

        // Title changed: rebuild slug to stay consistent with the title
        String newSlug = SlugUtils.toSlug(request.title());
        if (!post.getSlug().equals(newSlug)) {
            if (postRepository.existsBySlugAndIdNot(newSlug, postId)) {
                throw new IllegalStateException("Ya existe un post con tal slug");
            }
            post.setSlug(newSlug);
        }

        return toResponse(post);
    }

    /**
     * Changes only the status of a post, applying stricter transition rules than
     * {@link #update} (which also updates content fields at the same time).
     *
     * @throws ResourceNotFoundException if the post does not exist (or does not belong to the user)
     * @throws IllegalArgumentException  if {@code newStatusStr} is not a valid {@link PostStatus} name
     * @throws IllegalStateException     if the requested transition is not permitted for authors,
     *                                    or if resubmitting (REJECTED → DRAFT) a post that has been
     *                                    permanently blocked by 3 accumulated rejections
     */
    public MyPostResponse updateStatus(Integer userId, Integer postId, String newStatusStr) {

        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + postId));

        // Convert String to Enum
        PostStatus newStatus;
        try {
            newStatus = PostStatus.valueOf(newStatusStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado no válido: " + newStatusStr);
        }

        // If the author is trying to republish a rejected post,
        // check first that it hasn't been permanently blocked
        if (post.getStatus() == PostStatus.REJECTED && newStatus == PostStatus.DRAFT) {
            if (!post.canBeResubmitted()) {
                throw new IllegalStateException(
                        "Este post ha sido rechazado 3 veces y está bloqueado permanentemente. "
                        + "Contactá al administrador para desbloquearlo."
                );
            }
        }

        validateAuthorCanChangeStatus(post.getStatus(), newStatus);

        post.setStatus(newStatus);
        postRepository.save(post);

        return toResponse(post);
    }

    /**
     * Deletes the post and its cover image from storage.
     * Deleting the cover image first prevents orphaned files if the DB delete fails.
     *
     * @throws ResourceNotFoundException if the post does not exist (or does not belong to the user)
     */
    public void delete(@NonNull Integer userId, @NonNull Integer postId) {
        Post post = postRepository
                .findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado"));

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

        return toResponse(post);
    }

    public MyPostResponse uploadCoverImage(@NonNull Integer userId, @NonNull Integer postId, MultipartFile image) {
        Post post = postRepository.findByIdAndAuthorId(postId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con id: " + postId));

        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("No se proporciono ninguna imagen");
        }

        String imageUrl = storageService.save(image, post.getCoverImage());

        post.setCoverImage(imageUrl);
        postRepository.save(post);

        return toResponse(post);
    }

    private void validateAuthorCanChangeStatus(PostStatus currentStatus, PostStatus newStatus) {
        if (currentStatus == newStatus) {
            return; // No status change, no validation needed
        }

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
                            currentStatus.getDisplayName(), newStatus.getDisplayName())
            );
        }
    }

    private MyPostResponse toResponse(Post post) {
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
                post.getUpdatedAt(),
                //Moderation fields
                post.getModerationNote(),
                post.getRejectionCount(),
                post.canBeResubmitted(),
                post.isLastAttempt()
        );
    }
}
