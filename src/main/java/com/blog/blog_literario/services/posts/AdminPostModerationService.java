package com.blog.blog_literario.services.posts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.services.images.StorageService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Admin service for post moderation: listing, status changes, and hard deletion.
 *
 * <p>All write operations are restricted to ADMIN-role users at the controller layer.
 * Deleting a post also removes its cover image from storage to prevent orphaned files.
 */
@Service
@RequiredArgsConstructor
public class AdminPostModerationService {

    private final PostRepository postRepository;
    private final StorageService storageService;

    /**
     * Returns a paginated list of posts, optionally filtered by {@code status}.
     * Passing {@code null} returns posts of all statuses.
     */
    @Transactional(readOnly = true)
    public Page<AdminPostResponse> listAll(PostStatus status, @NonNull Pageable pageable) {
        Page<Post> posts;

        if (status != null) {
            posts = postRepository.findByStatus(status, pageable);
        } else {
            posts = postRepository.findAll(pageable);
        }
        return posts.map(this::toResponse);
    }

    /**
     * Updates the status of a post to the value specified in {@code request}.
     *
     * @throws ResourceNotFoundException if no post exists with the given {@code postId}
     * @throws IllegalArgumentException  if {@code request.status()} is not a valid {@link PostStatus} name
     */
    @Transactional
    public AdminPostResponse updateStatus(@NonNull Integer postId, AdminStatusUpdateRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con ID: " + postId));

        PostStatus newStatus;
        try {
            newStatus = PostStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + request.status() + "'. Estados válidos: DRAFT, PUBLISHED, ARCHIVED, REJECTED"
            );
        }
        post.setStatus(newStatus);
        postRepository.save(post);
        return toResponse(post);
    }

    /**
     * Permanently deletes a post and its cover image from storage.
     *
     * @throws ResourceNotFoundException if no post exists with the given {@code postId}
     */
    @Transactional
    public void delete(@NonNull Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con id: " + postId));

        storageService.delete(post.getCoverImage());
        postRepository.delete(post);
    }

    private AdminPostResponse toResponse(Post post) {
        return new AdminPostResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getStatus().name(),
                post.getStatus().getDisplayName(),
                post.getAuthor().getName(),
                post.getAuthor().getEmail(),
                post.getCategory().getName(),
                storageService.buildUrl(post.getCoverImage()),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
