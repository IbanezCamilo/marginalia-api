package com.blog.blog_literario.services.email;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.blog.blog_literario.config.properties.FrontendProperties;
import com.blog.blog_literario.events.AuthorRequestSubmitted;

@ExtendWith(MockitoExtension.class)
class AuthorRequestNotificationListenerTest {

    @Mock EmailService emailService;

    @Test
    void onAuthorRequestSubmitted_buildsAdminPanelUrlAndDelegates() {
        AuthorRequestNotificationListener listener = new AuthorRequestNotificationListener(
                emailService, new FrontendProperties("http://localhost:5173"));
        var event = new AuthorRequestSubmitted(
                7, "Reader", "reader@test.com", "I want to write",
                List.of("admin@test.com", "owner@test.com"), "author-request/7");

        listener.onAuthorRequestSubmitted(event);

        verify(emailService).sendAuthorRequestNotification(
                List.of("admin@test.com", "owner@test.com"),
                "Reader", "reader@test.com", "I want to write",
                "http://localhost:5173/user/solicitudes",
                "author-request/7");
    }

    @Test
    void onAuthorRequestSubmitted_swallowsSendFailures() {
        AuthorRequestNotificationListener listener = new AuthorRequestNotificationListener(
                emailService, new FrontendProperties("http://localhost:5173"));
        willThrow(new RuntimeException("boom"))
                .given(emailService).sendAuthorRequestNotification(anyList(), any(), any(), any(), any(), any());
        var event = new AuthorRequestSubmitted(
                7, "Reader", "reader@test.com", "I want to write",
                List.of("admin@test.com"), "author-request/7");

        assertThatCode(() -> listener.onAuthorRequestSubmitted(event))
                .doesNotThrowAnyException();
    }
}
