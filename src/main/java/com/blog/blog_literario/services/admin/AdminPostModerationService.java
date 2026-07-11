package com.blog.blog_literario.services.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.admin.AdminPostResponse;
import com.blog.blog_literario.dto.admin.AdminStatusUpdateRequest;
import com.blog.blog_literario.exception.ResourceNotFoundException;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.repositories.PostRepository;
import com.blog.blog_literario.repositories.UserRepository;
import com.blog.blog_literario.services.images.StorageService;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Admin service for post moderation: listing, status changes, permanent-block resets,
 * and hard deletion.
 *
 * <p>All write operations are restricted to ADMIN-role users at the controller layer.
 * Admins may perform any status transition (including those unavailable to moderators,
 * such as touching {@code ARCHIVED} posts), <strong>except</strong> on a post that is
 * already permanently blocked (3+ rejections) — that state can only be cleared via
 * {@link #resetPost}, which clears the rejection count and moderation metadata
 * consistently. Deleting a post also removes its cover image from storage to prevent
 * orphaned files. Every override and destructive action here is recorded via
 * {@link AdminActionLogService}.
 */
@Service
@RequiredArgsConstructor
public class AdminPostModerationService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AdminActionLogService adminActionLogService;

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
     * @throws IllegalStateException     if the post is permanently blocked (3+ rejections) —
     *                                    use {@link #resetPost} to clear that state first
     */
    @Transactional
    public AdminPostResponse updateStatus(
            @NonNull Integer adminId,
            @NonNull Integer postId,
            @NonNull AdminStatusUpdateRequest request) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post no encontrado con ID: " + postId));

        if (post.isPermanentlyBlocked()) {
            throw new IllegalStateException(
                    "El post con ID " + postId + " está bloqueado permanentemente. "
                    + "Usá el endpoint de reset para desbloquearlo antes de cambiar su estado."
            );
        }

        PostStatus previousStatus = post.getStatus();
        PostStatus newStatus = parseStatus(request.status());

        if (newStatus == PostStatus.REJECTED) {
            handleRejection(post, adminId, request.moderationNote());
        } else {
            User admin = getUser(adminId);
            post.setStatus(newStatus);
            post.recordModeration(admin, request.moderationNote());
        }

        postRepository.save(post);

        // Fetched after the validations above so a not-found admin never masks a more
        // specific error (e.g. a blank moderation note) — matches the order callers expect.
        User admin = getUser(adminId);
        adminActionLogService.record(
                adminId, admin.getEmail(), "POST_STATUS_FORCE", "POST", postId,
                previousStatus.name() + " -> " + post.getStatus().name()
                        + (request.moderationNote() != null ? " (" + request.moderationNote() + ")" : "")
        );

        return toResponse(post);
    }

    /**
     * Unlocks a permanently-blocked post (3 accumulated rejections), returning it to
     * {@code DRAFT} with its rejection count and moderation note cleared via
     * {@link Post#resetForAuthor()}, then records the admin who performed the reset
     * and an optional note for the author.
     *
     * @throws ResourceNotFoundException if no post or admin exists for the given IDs
     * @throws IllegalStateException     if the post is not currently permanently blocked
     */
    @Transactional
    public AdminPostResponse resetPost(
            @NonNull Integer adminId,
            @NonNull Integer postId,
            String moderationNote) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post no encontrado con ID: " + postId));

        if (!post.isPermanentlyBlocked()) {
            throw new IllegalStateException(
                    "El post con ID " + postId + " no está bloqueado permanentemente. "
                    + "Solo se pueden desbloquear posts con 3 rechazos acumulados."
            );
        }

        int rejectionCountBeforeReset = post.getRejectionCount();

        // resetForAuthor() sets status=DRAFT, rejectionCount=0, clears note and moderation metadata
        post.resetForAuthor();

        // Re-record who did the reset and leave an optional note for the author
        User admin = getUser(adminId);
        post.recordModeration(admin, moderationNote);

        postRepository.save(post);

        adminActionLogService.record(
                adminId, admin.getEmail(), "POST_RESET", "POST", postId,
                "rejectionCount " + rejectionCountBeforeReset + " -> 0"
                        + (moderationNote != null ? " (" + moderationNote + ")" : "")
        );

        return toResponse(post);
    }

    /**
     * Permanently deletes a post and its cover image from storage.
     *
     * @throws ResourceNotFoundException if no post exists with the given {@code postId}
     */
    @Transactional
    public void delete(@NonNull Integer adminId, @NonNull Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post no encontrado con id: " + postId));

        User admin = getUser(adminId);

        storageService.delete(post.getCoverImage());
        postRepository.delete(post);

        adminActionLogService.record(
                adminId, admin.getEmail(), "POST_DELETE", "POST", postId,
                "title=" + post.getTitle()
        );
    }

    /**
     * Rejects {@code post}, requiring a non-blank {@code moderationNote} and
     * incrementing its rejection count. On the 3rd rejection the post is
     * automatically moved to {@code ARCHIVED} (permanently blocked) instead of
     * {@code REJECTED}.
     *
     * @throws IllegalStateException if {@code moderationNote} is blank
     */
    private void handleRejection(Post post, Integer adminId, String moderationNote) {
        if (moderationNote == null || moderationNote.isBlank()) {
            throw new IllegalStateException(
                    "La nota del moderador es obligatoria al rechazar un post. "
                    + "El autor necesita saber qué debe corregir."
            );
        }

        User admin = getUser(adminId);
        post.incrementRejectionCount();

        if (post.isPermanentlyBlocked()) {
            // 3rd rejection → archive automatically instead of leaving it as REJECTED
            post.setStatus(PostStatus.ARCHIVED);
        } else {
            post.setStatus(PostStatus.REJECTED);
        }

        post.recordModeration(admin, moderationNote);
    }

    private PostStatus parseStatus(String status) {
        try {
            return PostStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + status + "'. "
                    + "Estados válidos: DRAFT, PUBLISHED, ARCHIVED, REJECTED"
            );
        }
    }

    private User getUser(Integer userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Usuario no encontrado con ID: " + userId));
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
                post.getCategory() != null ? post.getCategory().getName() : null,
                storageService.buildUrl(post.getCoverImage()),
                post.getModerationNote(),
                post.getModeratedBy() != null ? post.getModeratedBy().getName() : null,
                post.getModeratedAt(),
                post.getRejectionCount(),
                post.isPermanentlyBlocked(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}
