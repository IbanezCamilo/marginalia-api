package com.blog.blog_literario.services.moderator;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.blog.blog_literario.dto.moderator.ModeratorPostResponse;
import com.blog.blog_literario.dto.moderator.ModeratorStatusUpdateRequest;
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
 * Moderator service for post moderation: listing and status changes.
 *
 * <p>Moderators may approve, reject, or archive posts (see {@link #validateTransition}
 * for the allowed transitions), but cannot touch already-{@code ARCHIVED} posts and
 * cannot unblock a permanently-blocked post — that requires an admin via
 * {@link com.blog.blog_literario.services.admin.AdminPostModerationService#resetPost}.
 */
@Service
@RequiredArgsConstructor
public class ModeratorPostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    /**
     * Returns a paginated list of posts, optionally filtered by {@code status}.
     * Passing {@code null} returns posts of all statuses.
     */
    @Transactional(readOnly = true)
    public Page<ModeratorPostResponse> listAll(PostStatus status, @NonNull Pageable pageable) {
        Page<Post> posts = (status != null)
                ? postRepository.findByStatus(status, pageable)
                : postRepository.findAll(pageable);

        return posts.map(this::toResponse);
    }

    /**
     * Updates the status of a post to the value specified in {@code request}.
     *
     * @throws ResourceNotFoundException if no post or moderator exists for the given IDs
     * @throws IllegalArgumentException  if {@code request.status()} is not a valid {@link PostStatus} name
     * @throws IllegalStateException     if the transition is not allowed for moderators, or if
     *                                    rejecting without a note / on an already-blocked post
     */
    @Transactional
    public ModeratorPostResponse updateStatus(
            @NonNull Integer moderatorId,
            @NonNull Integer postId,
            @NonNull ModeratorStatusUpdateRequest request) {

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Post no encontrado con ID: " + postId));

        PostStatus newStatus = parseStatus(request.status());

        validateTransition(post.getStatus(), newStatus);

        if (newStatus == PostStatus.REJECTED) {
            handleRejection(post, moderatorId, request.moderationNote());
        } else {
            User moderator = getModerator(moderatorId);
            post.setStatus(newStatus);
            post.recordModeration(moderator, request.moderationNote());
        }

        postRepository.save(post);
        return toResponse(post);
    }

    /**
     * Enforces the status transitions moderators are allowed to perform:
     * <ul>
     *   <li>{@code DRAFT} → {@code PUBLISHED} or {@code REJECTED}</li>
     *   <li>{@code PUBLISHED} → {@code REJECTED} or {@code ARCHIVED}</li>
     *   <li>{@code REJECTED} → {@code PUBLISHED} or {@code DRAFT}</li>
     *   <li>{@code ARCHIVED} → nothing (admin-only)</li>
     * </ul>
     *
     * @throws IllegalStateException if {@code next} is not reachable from {@code current}
     */
    private void validateTransition(PostStatus current, PostStatus next) {
        boolean allowed = switch (current) {
            case DRAFT      -> next == PostStatus.PUBLISHED || next == PostStatus.REJECTED;
            case PUBLISHED  -> next == PostStatus.REJECTED  || next == PostStatus.ARCHIVED;
            case REJECTED   -> next == PostStatus.PUBLISHED || next == PostStatus.DRAFT;
            case ARCHIVED   -> false; // moderators cannot touch archived posts
        };

        if (!allowed) {
            throw new IllegalStateException(
                    "Transición no permitida para moderadores: "
                    + current.name() + " → " + next.name()
                    + ". Contactá al administrador para realizar esta acción."
            );
        }
    }

    /**
     * Rejects {@code post}, requiring a non-blank {@code moderationNote} and
     * incrementing its rejection count. On the 3rd rejection the post is
     * automatically moved to {@code ARCHIVED} (permanently blocked) instead of
     * {@code REJECTED}.
     *
     * @throws IllegalStateException if {@code moderationNote} is blank, or the post
     *                                is already permanently blocked
     */
    private void handleRejection(Post post, Integer moderatorId, String moderationNote) {
        if (moderationNote == null || moderationNote.isBlank()) {
            throw new IllegalStateException(
                    "La nota es obligatoria al rechazar un post. "
                    + "El autor necesita saber qué debe corregir."
            );
        }

        if (post.isPermanentlyBlocked()) {
            throw new IllegalStateException(
                    "El post ID " + post.getId() + " ya está bloqueado permanentemente. "
                    + "Solo un administrador puede desbloquearlo."
            );
        }

        User moderator = getModerator(moderatorId);
        post.incrementRejectionCount();

        if (post.isPermanentlyBlocked()) {
            // 3rd rejection → archive automatically
            post.setStatus(PostStatus.ARCHIVED);
        } else {
            post.setStatus(PostStatus.REJECTED);
        }

        post.recordModeration(moderator, moderationNote);
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

    private User getModerator(Integer moderatorId) {
        return userRepository.findById(moderatorId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Moderador no encontrado con ID: " + moderatorId));
    }


    private ModeratorPostResponse toResponse(Post post) {
        return new ModeratorPostResponse(
                post.getId(),
                post.getTitle(),
                post.getSlug(),
                post.getStatus().name(),
                post.getStatus().getDisplayName(),
                post.getAuthor().getId(),
                post.getAuthor().getName(),
                post.getCategory().getName(),
                storageService.buildUrl(post.getCoverImage()),
                post.getModerationNote(),
                post.getModeratedBy() != null ? post.getModeratedBy().getName() : null,
                post.getModeratedAt(),
                post.getRejectionCount(),
                post.isPermanentlyBlocked(),
                post.isLastAttempt(),
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }
}