package com.blog.blog_literario.services.moderator;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.blog.blog_literario.events.PostModerationStatusChanged;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.UserPreference;
import com.blog.blog_literario.services.users.UserPreferenceService;

import lombok.RequiredArgsConstructor;

/**
 * Publishes {@link PostModerationStatusChanged} for a moderated post. Shared by
 * the moderator and admin moderation services so the skip rules live in one place:
 * no event when the status didn't actually change, when the actor is the author
 * (no self-notifications), or when the author disabled moderation emails.
 *
 * <p>Must be called inside the moderation transaction, after the post has been
 * mutated — the preference read and the skip decisions happen here so the
 * after-commit listener never touches repositories.
 */
@Component
@RequiredArgsConstructor
public class PostModerationEventPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final UserPreferenceService userPreferenceService;

    public void publishStatusChange(Post post, Integer actorId, PostStatus previousStatus) {
        if (post.getStatus() == previousStatus) {
            return;
        }
        Integer authorId = post.getAuthor().getId();
        if (authorId.equals(actorId)) {
            return;
        }
        if (!userPreferenceService.isEnabled(authorId, UserPreference.POST_MODERATION_EMAILS)) {
            return;
        }

        String title = (post.getTitle() != null && !post.getTitle().isBlank())
                ? post.getTitle() : "(sin título)";

        eventPublisher.publishEvent(new PostModerationStatusChanged(
                post.getId(),
                title,
                post.getAuthor().getName(),
                post.getAuthor().getEmail(),
                previousStatus,
                post.getStatus(),
                post.getModerationNote(),
                "post-moderation/" + post.getId() + "/" + UUID.randomUUID()));
    }
}
