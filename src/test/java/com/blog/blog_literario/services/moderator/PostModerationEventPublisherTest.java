package com.blog.blog_literario.services.moderator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.blog.blog_literario.events.PostModerationStatusChanged;
import com.blog.blog_literario.model.Post;
import com.blog.blog_literario.model.PostStatus;
import com.blog.blog_literario.model.Role;
import com.blog.blog_literario.model.User;
import com.blog.blog_literario.model.UserPreference;
import com.blog.blog_literario.services.users.UserPreferenceService;

@ExtendWith(MockitoExtension.class)
class PostModerationEventPublisherTest {

    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserPreferenceService userPreferenceService;

    @InjectMocks PostModerationEventPublisher publisher;

    private User author;
    private Post post;

    @BeforeEach
    void setUp() {
        author = new User(2, "Alice", "alice@test.com", new Role("AUTHOR"));
        post = new Post("Mi cuento", "Contenido", PostStatus.PUBLISHED, "mi-cuento", author, null);
        post.setId(10);
    }

    @Test
    void publishStatusChange_statusChanged_publishesEventWithFields() {
        given(userPreferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS))
                .willReturn(true);

        publisher.publishStatusChange(post, 1, PostStatus.DRAFT);

        ArgumentCaptor<PostModerationStatusChanged> captor =
                ArgumentCaptor.forClass(PostModerationStatusChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        PostModerationStatusChanged event = captor.getValue();
        assertThat(event.postId()).isEqualTo(10);
        assertThat(event.postTitle()).isEqualTo("Mi cuento");
        assertThat(event.authorName()).isEqualTo("Alice");
        assertThat(event.authorEmail()).isEqualTo("alice@test.com");
        assertThat(event.previousStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(event.newStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(event.idempotencyKey()).startsWith("post-moderation/10/");
    }

    @Test
    void publishStatusChange_untitledDraft_usesFallbackTitle() {
        Post untitled = new Post(null, "Contenido", PostStatus.REJECTED, null, author, null);
        untitled.setId(11);
        given(userPreferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS))
                .willReturn(true);

        publisher.publishStatusChange(untitled, 1, PostStatus.DRAFT);

        ArgumentCaptor<PostModerationStatusChanged> captor =
                ArgumentCaptor.forClass(PostModerationStatusChanged.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().postTitle()).isEqualTo("(sin título)");
    }

    @Test
    void publishStatusChange_sameStatus_publishesNothing() {
        publisher.publishStatusChange(post, 1, PostStatus.PUBLISHED);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishStatusChange_actorIsAuthor_publishesNothing() {
        publisher.publishStatusChange(post, 2, PostStatus.DRAFT);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publishStatusChange_preferenceDisabled_publishesNothing() {
        given(userPreferenceService.isEnabled(2, UserPreference.POST_MODERATION_EMAILS))
                .willReturn(false);

        publisher.publishStatusChange(post, 1, PostStatus.DRAFT);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
