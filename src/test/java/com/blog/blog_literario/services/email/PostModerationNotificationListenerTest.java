package com.blog.blog_literario.services.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.PostModerationStatusChanged;
import com.blog.blog_literario.model.PostStatus;

@ExtendWith(MockitoExtension.class)
class PostModerationNotificationListenerTest {

    @Mock EmailService emailService;

    private PostModerationStatusChanged event() {
        return new PostModerationStatusChanged(
                10, "Mi cuento", "Alice", "alice@test.com",
                PostStatus.DRAFT, PostStatus.PUBLISHED, "Buen trabajo",
                "post-moderation/10/abc");
    }

    @Test
    void onPostModerationStatusChanged_buildsPostsUrlAndDelegates() {
        PostModerationNotificationListener listener = new PostModerationNotificationListener(
                emailService, new FrontendProperties("http://localhost:5173"));

        listener.onPostModerationStatusChanged(event());

        verify(emailService).sendPostModerationNotification(
                "alice@test.com", "Alice", "Mi cuento",
                PostStatus.DRAFT, PostStatus.PUBLISHED, "Buen trabajo",
                "http://localhost:5173/user/posts",
                "post-moderation/10/abc");
    }

    @Test
    void onPostModerationStatusChanged_swallowsSendFailures() {
        PostModerationNotificationListener listener = new PostModerationNotificationListener(
                emailService, new FrontendProperties("http://localhost:5173"));
        willThrow(new RuntimeException("boom")).given(emailService)
                .sendPostModerationNotification(any(), any(), any(), any(), any(), any(), any(), any());

        assertThatCode(() -> listener.onPostModerationStatusChanged(event()))
                .doesNotThrowAnyException();
    }
}
